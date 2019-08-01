package com.kimjisub.design;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PadBackLight extends GLSurfaceView {

	private int XNum;
	private int YNum;

	private int width;
	private int height;

	public PadBackLight(Context context, int XNum, int YNum) {
		super(context);
		this.XNum = XNum;
		this.YNum = YNum;

		init();
	}

	private void init() {
		setEGLContextClientVersion(2);

		// GLSurfaceView에 그래픽 객체를 그리는 처리를 하는 renderer를 설정합니다.
		setRenderer(new Renderer() {
			@Override
			public void onSurfaceCreated(GL10 gl, EGLConfig config) {
				GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
			}

			@Override
			public void onSurfaceChanged(GL10 gl, int width_, int height_) {
				width = width_;
				height = height_;
				//viewport를 설정합니다.
				//specifies the affine transformation of x and y from
				//normalized device coordinates to window coordinates
				//viewport rectangle의 왼쪽 아래를 (0,0)으로 지정하고
				//viewport의 width와 height를 지정합니다.
				GLES20.glViewport(0, 0, width, height);
			}

			@Override
			public void onDrawFrame(GL10 gl) {
				//glClearColor에서 설정한 값으로 color buffer를 클리어합니다.
				//glClear메소드를 사용하여 클리어할 수 있는 버퍼는 다음 3가지 입니다.
				//Color buffer (GL_COLOR_BUFFER_BIT)
				//depth buffer (GL_DEPTH_BUFFER_BIT)
				//stencil buffer (GL_STENCIL_BUFFER_BIT)
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			}
		});


		//Surface가 생성될때와 GLSurfaceView클래스의 requestRender 메소드가 호출될때에만
		//화면을 다시 그리게 됩니다.
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	void setLED(int x, int y, int color) {

	}

	void removeLED(int x, int y) {

	}
}