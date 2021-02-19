package me.hydos.mowingsim.engine.io;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.hydos.mowingsim.engine.Rosella;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.VK10.vkEnumerateInstanceExtensionProperties;

public class Screen {

	public final String title;
	public final int width, height;
	private final long windowPtr;
	private final List<MainLoopCallback> callbacks = new ObjectArrayList<>();

	public Screen(String title, int width, int height, boolean windowResizable) {
		this.title = title;
		this.width = width;
		this.height = height;
		glfwInit();

		windowPtr = glfwCreateWindow(width, height, title, 0, 0);
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_RESIZABLE, windowResizable ? GLFW_TRUE : GLFW_FALSE);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			glfwDestroyWindow(windowPtr);
			glfwTerminate();
		}));
	}

	public void start(Rosella engine) {
		while (!glfwWindowShouldClose(windowPtr)) {
			glfwPollEvents();
			for (MainLoopCallback callback : callbacks) {
				callback.run();
			}
		}
	}

	public void onMainLoop(MainLoopCallback callback) {
		callbacks.add(callback);
	}

	public interface MainLoopCallback {
		void run();
	}
}
