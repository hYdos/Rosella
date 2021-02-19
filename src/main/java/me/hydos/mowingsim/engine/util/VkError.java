package me.hydos.mowingsim.engine.util;

public class VkError {

	public static void check(int returnCode) {
		if(returnCode != 0) {
			System.out.println(returnCode);
			System.exit(-2);
		}
	}
}
