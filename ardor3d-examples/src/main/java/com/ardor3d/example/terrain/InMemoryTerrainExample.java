/**
 * Copyright (c) 2008-2021 Bird Dog Games, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <https://git.io/fjRmv>.
 */

package com.ardor3d.example.terrain;

import com.ardor3d.example.ExampleBase;
import com.ardor3d.example.Purpose;
import com.ardor3d.extension.terrain.client.Terrain;
import com.ardor3d.extension.terrain.client.TerrainBuilder;
import com.ardor3d.extension.terrain.client.TerrainDataProvider;
import com.ardor3d.extension.terrain.providers.inmemory.InMemoryTerrainDataProvider;
import com.ardor3d.extension.terrain.providers.inmemory.data.InMemoryTerrainData;
import com.ardor3d.framework.CanvasRenderer;
import com.ardor3d.image.Texture;
import com.ardor3d.input.keyboard.Key;
import com.ardor3d.input.logical.InputTrigger;
import com.ardor3d.input.logical.KeyPressedCondition;
import com.ardor3d.intersection.PickingUtil;
import com.ardor3d.intersection.PrimitivePickResults;
import com.ardor3d.light.DirectionalLight;
import com.ardor3d.light.LightProperties;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector3;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.RenderContext;
import com.ardor3d.renderer.Renderer;
import com.ardor3d.renderer.queue.RenderBucketType;
import com.ardor3d.renderer.state.CullState;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.extension.Skybox;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.shape.Sphere;
import com.ardor3d.ui.text.BasicText;
import com.ardor3d.util.GameTaskQueue;
import com.ardor3d.util.GameTaskQueueManager;
import com.ardor3d.util.ReadOnlyTimer;
import com.ardor3d.util.TextureManager;

/**
 * Example showing the Geometry Clipmap Terrain system with 'MegaTextures' streaming from an
 * in-memory data source. Requires GLSL support.
 */
@Purpose(
    htmlDescriptionKey = "com.ardor3d.example.terrain.InMemoryTerrainExample", //
    thumbnailPath = "com/ardor3d/example/media/thumbnails/terrain_InMemoryTerrainExample.jpg", //
    maxHeapMemory = 128)
public class InMemoryTerrainExample extends ExampleBase {

  private boolean updateTerrain = true;
  private final float farPlane = 8000.0f;

  private Terrain terrain;

  private final Sphere sphere = new Sphere("sp", 16, 16, 1);
  private final Ray3 pickRay = new Ray3();

  private boolean groundCamera = false;
  private Camera terrainCamera;
  private Skybox skybox;

  private InMemoryTerrainData inMemoryTerrainData;

  /** Text fields used to present info about the example. */
  private final BasicText _exampleInfo[] = new BasicText[6];

  public static void main(final String[] args) {
    ExampleBase.start(InMemoryTerrainExample.class);
  }

  @Override
  protected void updateExample(final ReadOnlyTimer timer) {
    final Camera camera = _canvas.getCanvasRenderer().getCamera();

    // Make sure camera is above terrain
    final double height = terrain.getHeightAt(camera.getLocation().getX(), camera.getLocation().getZ());
    if (height > -Float.MAX_VALUE && (groundCamera || camera.getLocation().getY() < height + 3)) {
      camera.setLocation(new Vector3(camera.getLocation().getX(), height + 3, camera.getLocation().getZ()));
    }

    if (updateTerrain) {
      terrainCamera.set(camera);
    }

    skybox.setTranslation(camera.getLocation());

    // if we're picking...
    if (sphere.getSceneHints().getCullHint() == CullHint.Dynamic) {
      // Set up our pick ray
      pickRay.setOrigin(camera.getLocation());
      pickRay.setDirection(camera.getDirection());

      // do pick and move the sphere
      final PrimitivePickResults pickResults = new PrimitivePickResults();
      pickResults.setCheckDistance(true);
      PickingUtil.findPick(_root, pickRay, pickResults);
      if (pickResults.getNumber() != 0) {
        final Vector3 intersectionPoint = pickResults.getPickData(0).getIntersectionRecord().getIntersectionPoint(0);
        sphere.setTranslation(intersectionPoint);
        // XXX: maybe change the color of the ball for valid vs. invalid?
      }
    }
  }

  /**
   * Initialize pssm pass and scene.
   */
  @Override
  protected void initExample() {
    Terrain.addDefaultResourceLocators();

    // Setup main camera.
    _canvas.setTitle("Terrain Example");
    _canvas.getCanvasRenderer().getCamera().setLocation(new Vector3(0, 300, 0));
    _canvas.getCanvasRenderer().getCamera().lookAt(new Vector3(1, 300, 1), Vector3.UNIT_Y);
    _canvas.getCanvasRenderer().getCamera().setFrustumPerspective(70.0,
        (float) _canvas.getCanvasRenderer().getCamera().getWidth()
            / _canvas.getCanvasRenderer().getCamera().getHeight(),
        1.0f, farPlane);
    final CanvasRenderer canvasRenderer = _canvas.getCanvasRenderer();
    final RenderContext renderContext = canvasRenderer.getRenderContext();
    final Renderer renderer = canvasRenderer.getRenderer();
    GameTaskQueueManager.getManager(renderContext).getQueue(GameTaskQueue.RENDER).enqueue(() -> {
      renderer.setBackgroundColor(ColorRGBA.GRAY);
      return null;
    });
    _controlHandle.setMoveSpeed(200);

    setupDefaultStates();

    sphere.getSceneHints().setAllPickingHints(false);
    sphere.getSceneHints().setCullHint(CullHint.Always);
    _root.attachChild(sphere);

    try {
      // Keep a separate camera to be able to freeze terrain update
      final Camera camera = _canvas.getCanvasRenderer().getCamera();
      terrainCamera = new Camera(camera);

      inMemoryTerrainData = new InMemoryTerrainData(2048, 9, 128, new Vector3(1, 200, 1));

      final TerrainDataProvider terrainDataProvider = new InMemoryTerrainDataProvider(inMemoryTerrainData, true);

      terrain = new TerrainBuilder(terrainDataProvider, terrainCamera).withShowDebugPanels(true).build();
      terrain.setRenderMaterial("clipmap/terrain_textured.yaml");

      _root.attachChild(terrain);
    } catch (final Exception ex1) {
      System.out.println("Problem setting up terrain...");
      ex1.printStackTrace();
    }

    skybox = buildSkyBox();
    skybox.getSceneHints().setAllPickingHints(false);
    _root.attachChild(skybox);

    // Setup labels for presenting example info.
    final Node textNodes = new Node("Text");
    _orthoRoot.attachChild(textNodes);
    textNodes.getSceneHints().setRenderBucketType(RenderBucketType.OrthoOrder);
    LightProperties.setLightReceiver(textNodes, false);

    final double infoStartY = _canvas.getCanvasRenderer().getCamera().getHeight() / 2;
    for (int i = 0; i < _exampleInfo.length; i++) {
      _exampleInfo[i] = BasicText.createDefaultTextLabel("Text", "", 16);
      _exampleInfo[i].setTranslation(new Vector3(10, infoStartY - i * 20, 0));
      textNodes.attachChild(_exampleInfo[i]);
    }

    textNodes.updateGeometricState(0.0);
    updateText();

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.V), (source, inputStates, tpf) -> {
      if (!inMemoryTerrainData.isRunning()) {
        inMemoryTerrainData.startUpdates();
      } else {
        inMemoryTerrainData.stopUpdates();
      }
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.U), (source, inputStates, tpf) -> {
      updateTerrain = !updateTerrain;
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.ONE), (source, inputStates, tpf) -> {
      _controlHandle.setMoveSpeed(5);
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.TWO), (source, inputStates, tpf) -> {
      _controlHandle.setMoveSpeed(50);
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.THREE), (source, inputStates, tpf) -> {
      _controlHandle.setMoveSpeed(400);
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.FOUR), (source, inputStates, tpf) -> {
      _controlHandle.setMoveSpeed(1000);
      updateText();
    }));

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.SPACE), (source, inputStates, tpf) -> {
      groundCamera = !groundCamera;
      updateText();
    }));

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.P), (source, inputStates, tpf) -> {
      if (sphere.getSceneHints().getCullHint() == CullHint.Dynamic) {
        sphere.getSceneHints().setCullHint(CullHint.Always);
      } else if (sphere.getSceneHints().getCullHint() == CullHint.Always) {
        sphere.getSceneHints().setCullHint(CullHint.Dynamic);
      }
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.R), (source, inputStates, tpf) -> {
      terrain.getTextureClipmap().setShowDebug(!terrain.getTextureClipmap().isShowDebug());
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.G), (source, inputStates, tpf) -> {}));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.FIVE), (source, inputStates, tpf) -> {
      terrain.getTextureClipmap().setPixelDensity(terrain.getTextureClipmap().getPixelDensity() / 2);
      updateText();
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.SIX), (source, inputStates, tpf) -> {
      terrain.getTextureClipmap().setPixelDensity(terrain.getTextureClipmap().getPixelDensity() * 2);
      updateText();
    }));

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.SEVEN), (source, inputStates, tpf) -> {
      final Camera camera = _canvas.getCanvasRenderer().getCamera();
      camera.setLocation(camera.getLocation().getX() + 500.0, camera.getLocation().getY(), camera.getLocation().getZ());
    }));

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.EIGHT), (source, inputStates, tpf) -> {
      final Camera camera = _canvas.getCanvasRenderer().getCamera();
      camera.setLocation(camera.getLocation().getX() - 500.0, camera.getLocation().getY(), camera.getLocation().getZ());
    }));
    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.NINE), (source, inputStates, tpf) -> {
      final Camera camera = _canvas.getCanvasRenderer().getCamera();
      camera.setLocation(camera.getLocation().getX(), camera.getLocation().getY(),
          camera.getLocation().getZ() + 1500.0);
    }));

    _logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.ZERO), (source, inputStates, tpf) -> {
      final Camera camera = _canvas.getCanvasRenderer().getCamera();
      camera.setLocation(camera.getLocation().getX(), camera.getLocation().getY(),
          camera.getLocation().getZ() - 1500.0);
    }));
  }

  @Override
  protected void setupLight() {
    final DirectionalLight dLight = new DirectionalLight();
    dLight.setEnabled(true);
    dLight.setColor(new ColorRGBA(0.6f, 0.6f, 0.5f, 1));
    dLight.lookAt(-1, -1, -1);
    _root.attachChild(dLight);
  }

  private void setupDefaultStates() {
    final CullState cs = new CullState();
    cs.setEnabled(true);
    cs.setCullFace(CullState.Face.Back);
    _root.setRenderState(cs);

    // final FogState fs = new FogState();
    // fs.setStart(farPlane / 2.0f);
    // fs.setEnd(farPlane);
    // fs.setColor(new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f));
    // fs.setDensityFunction(DensityFunction.Linear);
    // _root.setRenderState(fs);
  }

  /**
   * Builds the sky box.
   */
  private Skybox buildSkyBox() {
    final Skybox skybox = new Skybox("skybox", 10, 10, 10);

    final String dir = "images/skybox/";
    final Texture north = TextureManager.load(dir + "1.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);
    final Texture south = TextureManager.load(dir + "3.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);
    final Texture east = TextureManager.load(dir + "2.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);
    final Texture west = TextureManager.load(dir + "4.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);
    final Texture up = TextureManager.load(dir + "6.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);
    final Texture down = TextureManager.load(dir + "5.jpg", Texture.MinificationFilter.BilinearNearestMipMap, true);

    skybox.setTexture(Skybox.Face.North, north);
    skybox.setTexture(Skybox.Face.West, west);
    skybox.setTexture(Skybox.Face.South, south);
    skybox.setTexture(Skybox.Face.East, east);
    skybox.setTexture(Skybox.Face.Up, up);
    skybox.setTexture(Skybox.Face.Down, down);
    skybox.setRenderMaterial("unlit/textured/basic.yaml");

    return skybox;
  }

  /**
   * Update text information.
   */
  private void updateText() {
    _exampleInfo[0].setText("[1/2/3] Moving speed: " + _controlHandle.getMoveSpeed() * 3.6 + " km/h");
    _exampleInfo[1].setText("[P] Do picking: " + (sphere.getSceneHints().getCullHint() == CullHint.Dynamic));
    _exampleInfo[2].setText("[SPACE] Toggle fly/walk: " + (groundCamera ? "walk" : "fly"));
    _exampleInfo[4].setText("[U] Freeze terrain(debug): " + !updateTerrain);
    _exampleInfo[5].setText("[V] Updating terrain data: " + inMemoryTerrainData.isRunning());
  }
}
