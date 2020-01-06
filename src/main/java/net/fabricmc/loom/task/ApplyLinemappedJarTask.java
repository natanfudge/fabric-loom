package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApplyLinemappedJarTask extends AbstractLoomTask {
	private Object mappedJar;
	private Object linemappedJar;

	@InputFile
	public File getMappedJar() {
		return getProject().file(mappedJar);
	}

	@OutputFile
	public File getLinemappedJar() {
		return getProject().file(linemappedJar);
	}


	public void setMappedJar(Object mappedJar) {
		this.mappedJar = mappedJar;
	}

	public void setLinemappedJar(Object linemappedJar) {
		this.linemappedJar = linemappedJar;
	}

	public static Path jarsBeforeLinemapping(LoomGradleExtension extension) {
		return Paths.get(extension.getUserCache().getPath(), "jarsBeforeLinemapping");
	}


	@TaskAction
	public void run() {
		Path mappedJarPath = getMappedJar().toPath();
		Path linemappedJarPath = getLinemappedJar().toPath();

		if (Files.exists(linemappedJarPath)) {
			try {
				Files.createDirectories(jarsBeforeLinemapping(getExtension()));
				// The original jars without the linemapping needs to be saved for incremental decompilation
				Files.copy(mappedJarPath, jarsBeforeLinemapping(getExtension()).resolve(mappedJarPath.getFileName()));
				Files.deleteIfExists(mappedJarPath);
				Files.copy(linemappedJarPath, mappedJarPath);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}


}
