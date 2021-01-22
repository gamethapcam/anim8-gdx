package com.github.tommyettinger;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.tommyettinger.anim8.*;

/**
 * This is from the NorthernLights demo in SquidLib-Demos, available
 * <a href="https://github.com/tommyettinger/SquidLib-Demos/tree/master/NorthernLights">here</a>.
 * <p>
 * Credit for the shader adaptation goes to angelickite , a very helpful user on the libGDX Discord.
 * The Discord can be found at <a href="https://discord.gg/crTrDEK">this link</a>.
 */
public class ShaderCaptureDemo extends ApplicationAdapter {

    private SpriteBatch batch;
    private Texture pixel;
    private ShaderProgram shader;

    private long startTime;
    private float seed;
    private int width, height;
    private String name;

    @Override
    public void create() {
        //Gdx.app.setLogLevel(Application.LOG_DEBUG);
        batch = new SpriteBatch();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.drawPixel(0, 0, 0xFFFFFFFF);
        pixel = new Texture(pixmap);
        startTime = TimeUtils.millis();

        ShaderProgram.pedantic = false;
        final String vertex =
                "attribute vec4 a_position;\n" +
                        "attribute vec4 a_color;\n" +
                        "attribute vec2 a_texCoord0;\n" +
                        "uniform mat4 u_projTrans;\n" +
                        "varying vec4 v_color;\n" +
                        "varying vec2 v_texCoords;\n" +
                        "\n" +
                        "void main()\n" +
                        "{\n" +
                        "   v_color = a_color;\n" +
                        "   v_color.a = v_color.a * (255.0/254.0);\n" +
                        "   v_texCoords = vec2(a_texCoord0.x, a_texCoord0.y);\n" +
                        "   gl_Position =  u_projTrans * a_position;\n" +
                        "}\n";
        final String fragment =
                "#ifdef GL_ES\n" +
                "#define LOWP lowp\n" +
                "precision mediump float;\n" +
                "#else\n" +
                "#define LOWP \n" +
                "#endif\n" +
                "\n" +
                "varying LOWP vec4 v_color;\n" +
                "varying vec2 v_texCoords;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform float seed;\n" +
                "uniform float tm;\n" +
                "\n" +
                "const float PHI = 1.618034; // phi, the Golden Ratio\n" +
                "\n" +
                "float swayRandomized(float seed, float value)\n" +
                "{\n" +
                "    float f = floor(value);\n" +
                "    float start = fract(fract((f - seed) * PHI + seed - f * 6.98765) * (PHI + f));\n" +
                "    float end   = fract(fract((f+1.0 - seed) * PHI + seed - (f + 1.0) * 6.98765) * (PHI + f+1.0));\n" +
                "    return mix(start, end, smoothstep(0., 1., value - f));\n" +
                "}\n" +
                "float cosmic(float seed, vec3 con)\n" +
                "{\n" +
                "    float sum = swayRandomized(seed, con.x + con.y + con.z) * 3.0;\n" +
                "    return -2.5 + (sum + 2.0 * swayRandomized(-seed, sum * 0.5698402909980532 + 0.7548776662466927 * (con.x + con.y + con.z - sum)));\n" +
                "}\n" +
                "void main() {\n" +
                "//  vec3 xyz = vec3(gl_FragCoord.xy, tm);\n" +
                "  vec2 distort = acos(1.5 * (v_texCoords - 0.5)) * pow(PHI, -2.75 + distance(v_texCoords, vec2(0.5, 0.75))) * 300.0;\n" +
                "  vec3 xyz = vec3(distort.x, (distort.y + 4.0) * sin(tm * (3.14159265 * 0.02)) * 0.3, (distort.y + 7.0) * cos(tm * (3.14159265 * 0.02)) * 0.3);\n" +
                "  vec3 alt = xyz * 0.009 - xyz.yzx * 0.005 + xyz.zxy * 0.003;\n" +
                "  \n" +
                "  float yt = (alt.y * PHI + alt.z - alt.x) * 0.5 * (swayRandomized(123.456 + seed, alt.x * 0.2123) + 1.5);\n" +
                "  float xt = (alt.z * PHI + alt.x - alt.y) * 0.5 * (swayRandomized(seed, alt.y * 0.2123) + 1.5);\n" +
                "  float xy = (alt.x * PHI + alt.y - alt.z) * 0.5 * (swayRandomized(789.123 - seed, alt.z * 0.2123) + 1.5);\n" +
                "  vec3 s = vec3(swayRandomized(-164.31527, xt - 3.11),\n" +
                "                swayRandomized(776.8142, 1.41 - xt),\n" +
                "                swayRandomized(-509.5190, xt + 2.61)) - 0.5;\n" +
                "  vec3 c = vec3(swayRandomized(-105.92407, yt - 1.11),\n" +
                "                swayRandomized(-615.6687, yt + 2.41),\n" +
                "                swayRandomized(435.8990, 3.61 - yt)) - 0.5;\n" +
                "  vec3 con = -swayRandomized(-seed, xyz.z * -0.04)\n" +
                "             + ((length(s) + length(c) + PHI)) * (vec3(\n" +
                "                  swayRandomized(924.10527, -2.4375 - xy),\n" +
                "                  swayRandomized(-566.50993, xy + 1.5625),\n" +
                "                  swayRandomized(-281.77664, xy + -3.8125))\n" +
                "                   * swayRandomized(1111.11 + seed, alt.z) + c * swayRandomized(11.1111 + seed, alt.x) + s * swayRandomized(111.111 + seed, alt.y));\n" +
                "  con.x = cosmic(seed, con);\n" +
                "  con.y = cosmic(seed + 123.456, con + PHI);\n" +
                "  con.z = cosmic(seed - 456.123, con - PHI);\n" +
                "  gl_FragColor.rgb = sin(con * 3.14159265) * 0.5 + 0.5;\n" +
                "  gl_FragColor.a = 1.0;\n" +
                "}\n";
        shader = new ShaderProgram(vertex, fragment);
        if (!shader.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shader:\n" + shader.getLog());
            Gdx.app.exit();
            return;
        }
        batch.setShader(shader);

//        long state = -1L; name = "pastel"; // pastel
        long state = 0x123456789L; name = "flashy"; // flashy, bw, gb
//        long state = 0x1234567890L; name = "green"; // green
        String[] nms = {"flashy", "bw", "gb", "pastel", "green"};
        int[][] pals = {null, {0x00000000, 0x000000FF, 0xFFFFFFFF}, {0x00000000, 0x081820FF, 0x346856FF, 0x88C070FF, 0xE0F8D0FF}, null, null};
        long[] sds = {0x123456789L, 0x123456789L, 0x123456789L, -1L, 0x1234567890L};

        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();

        Gdx.files.local("images").mkdirs();
		renderAPNG(nms, sds); // comment this out if you aren't using the full-color animated PNGs, because this is slow.
		renderPNG8(nms, pals, sds);
        renderGif(nms, pals, sds);
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && UIUtils.alt()) {
            if (Gdx.graphics.isFullscreen())
                Gdx.graphics.setWindowedMode(480, 320);
            else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        }
        Gdx.graphics.setTitle(Gdx.graphics.getFramesPerSecond() + " FPS");
        final float ftm = TimeUtils.timeSinceMillis(startTime) * 0x1p-5f;
        batch.begin();
        shader.setUniformf("seed", seed);
        shader.setUniformf("tm", ftm);
        batch.draw(pixel, 0, 0, width, height);
        batch.end();
    }

    public void renderAPNG(String[] names, long[] seeds) {
        AnimatedPNG apng = new AnimatedPNG();
        apng.setCompression(7);
        for (int n = 0; n < names.length && n < seeds.length; n++) {
            name = names[n];
            long state = seeds[n];
            // SquidLib's DiverRNG.randomize()
            seed = ((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.5bf0a8p-16f;
            Array<Pixmap> pixmaps = new Array<>(40);
            for (int i = 1; i <= 40; i++) {
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
                batch.begin();
                shader.setUniformf("seed", seed);
                shader.setUniformf("tm", i * 2.5f);
                batch.draw(pixel, 0, 0, width, height);
                batch.end();
                pixmaps.add(ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
            }
            apng.write(Gdx.files.local("images/AnimatedPNG-" + startTime + ".png"), pixmaps, 16);
            for (Pixmap pm : pixmaps)
                pm.dispose();
        }
    }
    
    public void renderPNG8(String[] names, int[][] palettes, long[] seeds) {
        PNG8 png8 = new PNG8();
        png8.setCompression(7);
        png8.palette = new PaletteReducer();
        for (int n = 0; n < names.length && n < palettes.length && n < seeds.length; n++) {
            name = names[n];
            long state = seeds[n];
            // SquidLib's DiverRNG.randomize()
            seed = ((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.5bf0a8p-16f;
            Array<Pixmap> pixmaps = new Array<>(40);
            for (int i = 1; i <= 40; i++) {
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
                batch.begin();
                shader.setUniformf("seed", seed);
                shader.setUniformf("tm", i * 2.5f);
                batch.draw(pixel, 0, 0, width, height);
                batch.end();
                pixmaps.add(ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
            }
            if (palettes[n] == null)
                png8.palette.analyze(pixmaps);
            else
                png8.palette.exact(palettes[n]);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.PATTERN);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-pattern.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.NONE);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-none.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.GRADIENT_NOISE);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-gradient.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.DIFFUSION);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-diffusion.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.BLUE_NOISE);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-blueNoise.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.CHAOTIC_NOISE);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-chaoticNoise.png"), pixmaps, 16);
            png8.setDitherAlgorithm(Dithered.DitherAlgorithm.SCATTER);
            png8.write(Gdx.files.local("images/PNG8-" + name + "-scatter.png"), pixmaps, 16);
            for (Pixmap pm : pixmaps)
                pm.dispose();
        }

        // black and white
//        png8.setPalette(new PaletteReducer(new int[]{0x00000000, 0x000000FF, 0xFFFFFFFF})); name = "bw";
        // gb palette
//        png8.setPalette(new PaletteReducer(new int[]{0x00000000, 0x081820FF, 0x346856FF, 0x88C070FF, 0xE0F8D0FF})); name = "gb";

    }

    public void renderGif(String[] names, int[][] palettes, long[] seeds) {
        AnimatedGif gif = new AnimatedGif();
        gif.palette = new PaletteReducer();
        for (int n = 0; n < names.length && n < palettes.length && n < seeds.length; n++) {
            name = names[n];
            long state = seeds[n];
            // SquidLib's DiverRNG.randomize()
            seed = ((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.5bf0a8p-16f;
            Array<Pixmap> pixmaps = new Array<>(40);
            for (int i = 1; i <= 40; i++) {
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
                batch.begin();
                shader.setUniformf("seed", seed);
                shader.setUniformf("tm", i * 2.5f);
                batch.draw(pixel, 0, 0, width, height);
                batch.end();
                pixmaps.add(ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
            }
            if (palettes[n] == null)
                gif.palette.analyze(pixmaps);
            else
                gif.palette.exact(palettes[n]);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.PATTERN);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-pattern.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.NONE);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-none.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.GRADIENT_NOISE);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-gradient.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.DIFFUSION);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-diffusion.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.BLUE_NOISE);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-blueNoise.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.CHAOTIC_NOISE);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-chaoticNoise.gif"), pixmaps, 16);
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.SCATTER);
            gif.write(Gdx.files.local("images/AnimatedGif-" + name + "-scatter.gif"), pixmaps, 16);
            for (Pixmap pm : pixmaps)
                pm.dispose();
        }
    }

	public static void main(String[] args) {
		createApplication();
	}

	private static Lwjgl3Application createApplication() {
		return new Lwjgl3Application(new ShaderCaptureDemo(), getDefaultConfiguration());
	}

	private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
		Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
		configuration.setTitle("Anim8-GDX Shader Capture Demo");
		configuration.setWindowedMode(256, 256);
		configuration.useVsync(true);
		configuration.setIdleFPS(20);
		ShaderProgram.prependFragmentCode = "#version 120\n";
		return configuration;
	}

}
