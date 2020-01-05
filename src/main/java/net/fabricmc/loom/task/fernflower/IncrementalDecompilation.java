package net.fabricmc.loom.task.fernflower;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class IncrementalDecompilation {
	/**
	 * Unchanged classfile paths will be outputted to the 'unchanged parameter', and changed ones will be outputed to 'changed'
	 * @return A directory that contains the changed classfiles
	 */
	public static Path diffCompiledJars(Path newCompiledJar, @Nullable Path oldCompiledJar, List<String> unchanged, List<String> changed) throws IOException {
		try (FileSystem newFs = FileSystems.newFileSystem(newCompiledJar, null)) {
			if (oldCompiledJar != null) {
				try (FileSystem oldFs = FileSystems.newFileSystem(oldCompiledJar, null)) {
					return addClassfiles(newFs, oldFs, unchanged, changed);
				}
			} else {
				return addClassfiles(newFs, null, unchanged, changed);
			}
		}
	}


	/**
	 * @return A directory that contains the changed classfiles
	 */
	private static Path addClassfiles(FileSystem jarFs, @Nullable FileSystem closestJarFs, List<String> unchanged, List<String> changed) throws IOException {
		// FF will only accept concrete files so we need to copy over the classfiles to a normal directory
		Path changedClassfilesDir = Files.createTempDirectory("compiled");

		for (Path rootDir : jarFs.getRootDirectories()) {
			Files.walk(rootDir).forEach(newJarPath -> {
				if (Files.isDirectory(newJarPath) || !newJarPath.toString().endsWith(".class")) return;
				try {
					if (outerOrInnerClassesChanged(closestJarFs, newJarPath)) {
						// Nothing to reuse from an old jar, decompile it

						Path tempClassfileForFF = Paths.get(changedClassfilesDir.toString(), newJarPath.toString());
						Files.createDirectories(tempClassfileForFF.getParent());
						Files.copy(newJarPath, tempClassfileForFF);

						changed.add(newJarPath.toString());
					} else {
						//TODO: filter out inner classes
						unchanged.add(newJarPath.toString());
					}

				} catch (IOException e) {
					throw new RuntimeException("Could not visit compiled jar file", e);
				}

			});
		}
		return changedClassfilesDir;
	}

	private static boolean changed(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
		Path classFileInOldJar = oldJarFs != null ? oldJarFs.getPath(classFile.toString()) : null;

		if (classFileInOldJar == null || !Files.exists(classFileInOldJar)) return true;
		byte[] newClassFile = Files.readAllBytes(classFile);
		byte[] oldClassFile = Files.readAllBytes(classFileInOldJar);
		return !Arrays.equals(newClassFile, oldClassFile);
	}

	/**
	 * In compiled jars, inner classes are separated into separate files, while in source jars, they are merged into one file.
	 * This means that when the inner class has changed we need to make sure the outer class gets decompiled too, and when
	 * and outer class gets changed we need to make sure the inner class gets decompiled too.
	 */
	private static boolean outerOrInnerClassesChanged(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
		String outerClassName = getOuterClassName(classFile);

		AtomicBoolean changed = new AtomicBoolean(false);
		Files.list(classFile.getParent()).forEach(pathInDir -> {
			try {
				if (changed.get()) return;
				if (Files.isDirectory(pathInDir)) return;
				if (getOuterClassName(pathInDir).equals(outerClassName) && changed(oldJarFs, pathInDir)) {
					changed.set(true);
				}
			} catch (IOException e) {
				throw new RuntimeException("Cannot check if classfile was changed", e);
			}
		});
		return changed.get();
	}

	private static String getOuterClassName(Path path) {
		String className = path.getFileName().toString();
		int index = className.indexOf("$");
		String withExtension = index == -1 ? className : className.substring(0, index);
		return withExtension.endsWith(".class") ? withExtension.
				substring(0, withExtension.length() - ".class".length()) : withExtension;
	}

	private static boolean isInnerClass(Path path) {
		return path.getFileName().toString().contains("$");
	}
}
