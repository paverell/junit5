/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.platform.console.tasks;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.junit.platform.engine.discovery.ClassFilter.includeClassNamePattern;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.platform.commons.util.PreconditionViolationException;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.console.options.CommandLineOptions;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

/**
 * @since 1.0
 */
class DiscoveryRequestCreator {

	LauncherDiscoveryRequest toDiscoveryRequest(CommandLineOptions options) {
		LauncherDiscoveryRequestBuilder requestBuilder = createRequestBuilder(options);
		addFilters(requestBuilder, options);
		return requestBuilder.build();
	}

	private LauncherDiscoveryRequestBuilder createRequestBuilder(CommandLineOptions options) {
		if (options.isRunAllTests()) {
			return createBuilderForAllTests(options);
		}
		return createNameBasedBuilder(options);
	}

	private LauncherDiscoveryRequestBuilder createBuilderForAllTests(CommandLineOptions options) {
		Set<File> rootDirectoriesToScan = determineClasspathRootDirectories(options);
		return request().selectors(selectClasspathRoots(rootDirectoriesToScan));
	}

	private Set<File> determineClasspathRootDirectories(CommandLineOptions options) {
		if (options.getArguments().isEmpty()) {
			Set<File> rootDirs = new LinkedHashSet<>(ReflectionUtils.getAllClasspathRootDirectories());
			if (!options.getAdditionalClasspathEntries().isEmpty()) {
				rootDirs.addAll(new ClasspathEntriesParser().toDirectories(options.getAdditionalClasspathEntries()));
			}
			return rootDirs;
		}
		return options.getArguments().stream().map(File::new).collect(toCollection(LinkedHashSet::new));
	}

	private LauncherDiscoveryRequestBuilder createNameBasedBuilder(CommandLineOptions options) {
		Preconditions.notEmpty(options.getArguments(), "No arguments were supplied to the ConsoleLauncher");
		return request().selectors(selectNames(options.getArguments()));
	}

	private void addFilters(LauncherDiscoveryRequestBuilder requestBuilder, CommandLineOptions options) {
		options.getIncludeClassNamePattern().ifPresent(
			pattern -> requestBuilder.filters(includeClassNamePattern(pattern)));

		if (!options.getIncludedTags().isEmpty()) {
			requestBuilder.filters(includeTags(options.getIncludedTags()));
		}

		if (!options.getExcludedTags().isEmpty()) {
			requestBuilder.filters(excludeTags(options.getExcludedTags()));
		}

		if (!options.getIncludedEngines().isEmpty()) {
			requestBuilder.filters(includeEngines(options.getIncludedEngines()));
		}

		if (!options.getExcludedEngines().isEmpty()) {
			requestBuilder.filters(excludeEngines(options.getExcludedEngines()));
		}
	}

	/**
	 * Create a list of {@link DiscoverySelector DiscoverySelectors} for the
	 * supplied names.
	 *
	 * <p>Consult the documentation for {@link #selectName(String)} for details
	 * on what types of names are supported.
	 *
	 * @param names the names to select; never {@code null}
	 * @return a list of {@code DiscoverySelectors} for the supplied names;
	 * potentially empty
	 */
	private static List<DiscoverySelector> selectNames(Collection<String> names) {
		Preconditions.notNull(names, "names collection must not be null");
		return names.stream().map(DiscoveryRequestCreator::selectName).collect(toList());
	}

	/**
	 * Create a {@link DiscoverySelector} for the supplied name.
	 *
	 * <h3>Supported Name Types</h3>
	 * <ul>
	 * <li>package: fully qualified package name</li>
	 * <li>class: fully qualified class name</li>
	 * <li>method: fully qualified method name</li>
	 * </ul>
	 *
	 * <p>The supported format for a <em>fully qualified method name</em> is
	 * {@code [fully qualified class name]#[methodName]}. For example, the
	 * fully qualified name for the {@code chars()} method in
	 * {@code java.lang.String} is {@code java.lang.String#chars}. Names for
	 * overloaded methods are not supported.
	 *
	 * @param name the name to select; never {@code null} or blank
	 * @return an instance of {@link ClassSelector}, {@link MethodSelector}, or
	 * {@link PackageSelector}
	 * @throws PreconditionViolationException if the supplied name is {@code null},
	 * blank, or does not specify a class, method, or package
	 */
	private static DiscoverySelector selectName(String name) throws PreconditionViolationException {
		Preconditions.notBlank(name, "name must not be null or blank");

		Optional<Class<?>> classOptional = ReflectionUtils.loadClass(name);
		if (classOptional.isPresent()) {
			return selectClass(classOptional.get());
		}

		Optional<Method> methodOptional = ReflectionUtils.loadMethod(name);
		if (methodOptional.isPresent()) {
			Method method = methodOptional.get();
			return selectMethod(method.getDeclaringClass(), method);
		}

		if (ReflectionUtils.isPackage(name)) {
			return selectPackage(name);
		}

		throw new PreconditionViolationException(
			String.format("'%s' specifies neither a class, a method, nor a package.", name));
	}

}
