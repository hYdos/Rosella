package me.hydos.rosella.util;

public class VkError {

	public static void check(int returnCode) {
		if(returnCode != 0) {
			System.out.println(returnCode);
			System.exit(-2);
		}
	}
}
