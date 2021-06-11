package me.hydos.rosella.render.model;

import java.util.function.Function;

class KotlinWTF {

	static Function<Float, Float> interpolate(float x1, float y1, float x2, float y2, float x3, float y3) {
		float l0d = (x1 - x2) * (x1 - x3);
		float l1d = (x2 - x1) * (x2 - x3);
		float l2d = (x3 - x1) * (x3 - x2);

		return x -> {
			float l_0 = (x - x2) * (x - x3);
			float l_1 = (x - x1) * (x - x3);
			float l_2 = (x - x1) * (x - x2);

			return y1 * l_0 / l0d + y2 * l_1 / l1d + y3 * l_2 / l2d;
		};
	}

	static Function<Float, Float> interpolate(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
		float l0d = (x1 - x2) * (x1 - x3) * (x1 - x4);
		float l1d = (x2 - x1) * (x2 - x3) * (x2 - x4);
		float l2d = (x3 - x1) * (x3 - x2) * (x3 - x4);
		float l3d = (x4 - x1) * (x4 - x2) * (x4 - x3);

		return x -> {
			float l_0 = (x - x2) * (x - x3) * (x - x4);
			float l_1 = (x - x1) * (x - x3) * (x - x4);
			float l_2 = (x - x1) * (x - x2) * (x - x4);
			float l_3 = (x - x1) * (x - x2) * (x - x3);

			return y1 * l_0 / l0d + y2 * l_1 / l1d + y3 * l_2 / l2d + y4 * l_3 / l3d;
		};
	}
}
