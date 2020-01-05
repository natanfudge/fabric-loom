package net.fabricmc.loom;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.loom.task.fernflower.ForkedFFExecutor;
import net.fabricmc.loom.task.fernflower.TinyJavadocProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class DecompilingTest {
	@Test
	public void testDecomp() {
		ForkedFFExecutor.runFF(new HashMap<String, Object>() {{
								   put("dgs", "1");
								   put("rsy", "1");
								   put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING,"1");
//								   put(IFabricJavadocProvider.PROPERTY_NAME,
//										   new TinyJavadocProvider(new File("yarn-1.15.1+build.23-v2.tiny")));
							   }}, new ArrayList<>(),
				Paths.get("minecraft-1.15.1-build.23.jar"),
				Paths.get("minecraft-1.15.1-build.17.jar"),
				Paths.get("minecraft-1.15.1-build.17-sources.jar"),
				new File("decomped.jar"),
				new File("linemap.txt"));
	}

}
