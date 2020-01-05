/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task.fernflower;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point for Forked FernFlower task.
 * Takes one parameter, a single file, each line is treated as command line input.
 * Forces one input file.
 * Forces one output file using '-o=/path/to/output'
 * Created by covers1624 on 11/02/19.
 */
public class ForkedFFExecutor {

	public static void main(String[] args) throws IOException {
		Map<String, Object> options = new HashMap<>();
		File input = null;
		File output = null;
		File lineMap = null;
		File mappings = null;
		List<File> libraries = new ArrayList<>();
		int numThreads = 0;

		boolean isOption = true;

		for (String arg : args) {
			if (isOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
				String value = arg.substring(5);

				if ("true".equalsIgnoreCase(value)) {
					value = "1";
				} else if ("false".equalsIgnoreCase(value)) {
					value = "0";
				}

				options.put(arg.substring(1, 4), value);
			} else {
				isOption = false;

				if (arg.startsWith("-e=")) {
					libraries.add(new File(arg.substring(3)));
				} else if (arg.startsWith("-o=")) {
					if (output != null) {
						throw new RuntimeException("Unable to set more than one output.");
					}

					output = new File(arg.substring(3));
				} else if (arg.startsWith("-l=")) {
					if (lineMap != null) {
						throw new RuntimeException("Unable to set more than one lineMap file.");
					}

					lineMap = new File(arg.substring(3));
				} else if (arg.startsWith("-m=")) {
					if (mappings != null) {
						throw new RuntimeException("Unable to use more than one mappings file.");
					}

					mappings = new File(arg.substring(3));
				} else if (arg.startsWith("-t=")) {
					numThreads = Integer.parseInt(arg.substring(3));
				} else {
					if (input != null) {
						throw new RuntimeException("Unable to set more than one input.");
					}

					input = new File(arg);
				}
			}
		}

		Objects.requireNonNull(input, "Input not set.");
		Objects.requireNonNull(output, "Output not set.");
		Objects.requireNonNull(mappings, "Mappings not set.");

		options.put(IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(mappings));
		runFF(options, libraries, input, output, lineMap);
	}

//		//TODO: only accept a list of changed files
//	//TODO: copy over manifest
	public static void runFF(Map<String, Object> options, List<File> libraries, File input, File output, File lineMap) {
		IResultSaver saver = new ThreadSafeResultSaver(() -> output, () -> lineMap);
		IFernflowerLogger logger = new ThreadIDFFLogger();
		Fernflower ff = new Fernflower(FernFlowerUtils::getBytecode, saver, options, logger);

		for (File library : libraries) {
			ff.addLibrary(library);
		}

		ff.addSource(input);
		ff.decompileContext();

		saver.closeArchive("", output.getName());

	}

//	/**
//	 * @return A list of unchanged classfiles, for which the source can be safely copied over from the old source jar
//	 */
//	private static List<Path> addClassfiles(FileSystem jarFs, Fernflower ff, @Nullable FileSystem closestJarFs) throws IOException {
//		// FF will only accept concrete files so we need to copy over the classfiles to a normal directory
//		Path changedClassfilesDir = Files.createTempDirectory("compiled");
//
//		AtomicInteger changedFiles = new AtomicInteger();
//		List<Path> unchangedClassfiles = new ArrayList<>();
//
//		for (Path rootDir : jarFs.getRootDirectories()) {
//			Files.walk(rootDir).forEach(newJarPath -> {
//				if (Files.isDirectory(newJarPath) || !newJarPath.toString().endsWith(".class")) return;
//				try {
//					if (outerOrInnerClassesChanged(closestJarFs, newJarPath)) {
//
//						// Nothing to reuse from an old jar, decompile it
//						Path tempClassfileForFF = Paths.get(changedClassfilesDir.toString(), newJarPath.toString());
//						Files.createDirectories(tempClassfileForFF.getParent());
//						Files.copy(newJarPath, tempClassfileForFF);
//						ff.addSource(tempClassfileForFF.toFile());
//						changedFiles.getAndIncrement();
//					} else {
//						// Inner classes are part of their outer classes in decompiled sources so we only need to copy the outer class
//						if (!isInnerClass(newJarPath)) unchangedClassfiles.add(newJarPath);
//					}
//
//				} catch (IOException e) {
//					throw new RuntimeException("Could not visit compiled jar file", e);
//				}
//
//			});
//		}
//
//		System.out.println("Decompiling " + changedFiles.get() + " classfiles");
//		System.out.println(unchangedClassfiles.size() + " classfiles are unchanged and will be copied as-is");
//		return unchangedClassfiles;
//	}
//
//	private static boolean changed(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
//		Path classFileInOldJar = oldJarFs != null ? oldJarFs.getPath(classFile.toString()) : null;
//
//		if (classFileInOldJar == null || !Files.exists(classFileInOldJar)) return true;
//		byte[] newClassFile = Files.readAllBytes(classFile);
//		byte[] oldClassFile = Files.readAllBytes(classFileInOldJar);
//		return !Arrays.equals(newClassFile, oldClassFile);
//	}
//
//	/**
//	 * In compiled jars, inner classes are separated into separate files, while in source jars, they are merged into one file.
//	 * This means that when the inner class has changed we need to make sure the outer class gets decompiled too, and when
//	 * and outer class gets changed we need to make sure the inner class gets decompiled too.
//	 */
//	private static boolean outerOrInnerClassesChanged(@Nullable FileSystem oldJarFs, Path classFile) throws IOException {
//		String outerClassName = getOuterClassName(classFile);
//
//		AtomicBoolean changed = new AtomicBoolean(false);
//		Files.list(classFile.getParent()).forEach(pathInDir -> {
//			try {
//				if (changed.get()) return;
//				if (Files.isDirectory(pathInDir)) return;
//				if (getOuterClassName(pathInDir).equals(outerClassName) && changed(oldJarFs, pathInDir)) {
//					changed.set(true);
//				}
//			} catch (IOException e) {
//				throw new RuntimeException("Cannot check if classfile was changed", e);
//			}
//		});
//		return changed.get();
//	}
//
//	private static String getOuterClassName(Path path) {
//		String className = path.getFileName().toString();
//		int index = className.indexOf("$");
//		String withExtension = index == -1 ? className : className.substring(0, index);
//		return withExtension.endsWith(".class") ? withExtension.
//				substring(0, withExtension.length() - CLASS_EXT_LENGTH) : withExtension;
//	}
//
//	private static boolean isInnerClass(Path path) {
//		return path.getFileName().toString().contains("$");
//	}

//	if (closestNamedJarSources != null) {
//				// Copy over unchanged sources
//				try (FileSystem oldSourcesFs = FileSystems.newFileSystem(closestNamedJarSources, null)) {
//					try (FileSystem newSourcesFs = FileSystems.newFileSystem(output.toPath(), null)) {
//						for (Path unchangedClassfile : unchangedClassfiles) {
//							String relativePathWithClassExtension = unchangedClassfile.toString();
//							String relativePath = relativePathWithClassExtension
//									.substring(0, relativePathWithClassExtension.length() - CLASS_EXT_LENGTH) + ".java";
//							Path targetPath = newSourcesFs.getPath(relativePath);
//							Files.createDirectories(targetPath.getParent());
//							Files.copy(oldSourcesFs.getPath(relativePath), targetPath);
//						}
//					}
//				}
//			}

}
