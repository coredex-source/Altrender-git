/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/net/irisshaders/iris/uniforms/custom/CustomUniforms.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.custom;

import java.util.Map;
import com.coredex.altshaders.uniform.stareval.element.ExpressionElement;
import com.coredex.altshaders.uniform.stareval.expression.Expression;
import com.coredex.altshaders.uniform.stareval.expression.VariableExpression;
import com.coredex.altshaders.uniform.stareval.function.FunctionContext;
import com.coredex.altshaders.uniform.stareval.function.FunctionReturn;
import com.coredex.altshaders.uniform.stareval.function.Type;
import com.coredex.altshaders.uniform.stareval.parser.Parser;
import com.coredex.altshaders.uniform.stareval.resolver.ExpressionResolver;
import com.coredex.altshaders.pack.PackEnv;
import com.coredex.altshaders.uniform.LocationalUniformHolder;
import com.coredex.altshaders.uniform.UniformHolder;
import com.coredex.altshaders.uniform.parsing.IrisFunctions;
import com.coredex.altshaders.uniform.parsing.IrisOptions;
import com.coredex.altshaders.uniform.parsing.VectorType;
import com.coredex.altshaders.uniform.custom.cached.CachedUniform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class CustomUniforms implements FunctionContext {
	private final Map<String, CachedUniform> variables = new java.util.LinkedHashMap<>();
	private final Map<String, Expression> variablesExpressions = new java.util.LinkedHashMap<>();
	private final CustomUniformFixedInputUniformsHolder inputHolder;
	private final List<CachedUniform> uniformOrder;
	private final Map<Object, java.util.Map<CachedUniform, Integer>> locationMap = new java.util.HashMap<>();
	private final Map<CachedUniform, List<CachedUniform>> dependsOn;

	private CustomUniforms(CustomUniformFixedInputUniformsHolder inputHolder, Map<String, Builder.Variable> variables) {
		this.inputHolder = inputHolder;
		ExpressionResolver resolver = new ExpressionResolver(
			IrisFunctions.functions,
			(name) -> {
				Type type = this.inputHolder.getType(name);
				if (type != null)
					return type;
				Builder.Variable variable = variables.get(name);
				if (variable != null)
					return variable.type;
				return null;
			},
			true);

		for (Builder.Variable variable : variables.values()) {
			try {
				Expression expression = resolver.resolveExpression(variable.type, variable.expression);
				CachedUniform cachedUniform = CachedUniform
					.forExpression(variable.name, variable.type, expression, this);
				this.addVariable(expression, cachedUniform);
				if (variable.uniform) {
					List<CachedUniform> uniforms = new ArrayList<>();
					uniforms.add(cachedUniform);
				}
				//PackEnv.LOG.info("Was able to resolve uniform " + variable.name + " = " + variable.expression);
			} catch (Exception e) {
				PackEnv.LOG
					.warn("Failed to resolve uniform " + variable.name + ", reason: " + e
						.getMessage() + " ( = " + variable.expression + ")", e);
			}
		}

		{
			// toposort

			this.dependsOn = new java.util.HashMap<>();
			Map<CachedUniform, List<CachedUniform>> requiredBy = new java.util.HashMap<>();
			java.util.Map<CachedUniform, Integer> dependsOnCount = new java.util.HashMap<>();

			for (CachedUniform input : this.inputHolder.getAll()) {
				requiredBy.put(input, new java.util.ArrayList<>());
			}

			for (CachedUniform input : this.variables.values()) {
				requiredBy.put(input, new java.util.ArrayList<>());
			}

			FunctionReturn functionReturn = new FunctionReturn();
			Set<VariableExpression> requires = new java.util.HashSet<>();
			Set<CachedUniform> brokenUniforms = new java.util.HashSet<>();

			for (Map.Entry<String, Expression> entry : this.variablesExpressions.entrySet()) {
				requires.clear();

				entry.getValue().listVariables(requires);
				if (requires.isEmpty()) {
					continue;
				}

				CachedUniform uniform = this.variables.get(entry.getKey());

				List<CachedUniform> dependencies = new ArrayList<>();
				for (VariableExpression v : requires) {
					Expression evaluated = v.partialEval(this, functionReturn);
					if (evaluated instanceof CachedUniform) {
						dependencies.add((CachedUniform) evaluated);
					} else {
						// we are depending on a broken uniform
						brokenUniforms.add(uniform);
					}
				}

				if (dependencies.isEmpty()) {
					// can be empty if we rely on broken uniforms
					continue;
				}

				dependsOn.put(uniform, dependencies);
				dependsOnCount.put(uniform, dependencies.size());

				for (CachedUniform dependency : dependencies) {
					requiredBy.get(dependency).add(uniform);
				}
			}

			// actual toposort:
			List<CachedUniform> ordered = new java.util.ArrayList<>();
			List<CachedUniform> free = new java.util.ArrayList<>();

			// init
			for (CachedUniform entry : requiredBy.keySet()) {
				if (!dependsOnCount.containsKey(entry)) {
					free.add(entry);
				}
			}

			while (!free.isEmpty()) {
				CachedUniform pop = free.removeLast();
				if (!brokenUniforms.contains(pop)) {
					// only add those that aren't broken
					ordered.add(pop);
				} else {
					// mark all those that rely on use as broken too
					brokenUniforms.addAll(requiredBy.get(pop));
				}
				for (CachedUniform dependent : requiredBy.get(pop)) {
					int count = dependsOnCount.merge(dependent, -1, Integer::sum);
					assert count >= 0;
					if (count == 0) {
						free.add(dependent);
						dependsOnCount.remove(dependent);
					}
				}
			}

			if (!brokenUniforms.isEmpty()) {
				PackEnv.LOG.warn(
					"The following uniforms won't work, either because they are broken, or reference a broken uniform: \n" +
						brokenUniforms.stream().map(CachedUniform::getName).collect(Collectors.joining(", ")));
			}

			if (!dependsOnCount.isEmpty()) {
				throw new IllegalStateException("Circular reference detected between: " +
					dependsOnCount.entrySet()
						.stream()
						.map(entry -> entry.getKey().getName() + " (" + entry.getValue() + ")")
						.collect(Collectors.joining(", "))
				);
			}

			this.uniformOrder = ordered;
		}
	}

	private void addVariable(Expression expression, CachedUniform uniform) throws Exception {
		String name = uniform.getName();
		if (this.variables.containsKey(name))
			throw new Exception("Duplicated variable: " + name);
		if (this.inputHolder.containsKey(name))
			throw new Exception("Variable shadows build in uniform: " + name);

		this.variables.put(name, uniform);
		this.variablesExpressions.put(name, expression);
	}

	public void assignTo(LocationalUniformHolder targetHolder) {
		java.util.Map<CachedUniform, Integer> locations = new java.util.HashMap<>();
		for (CachedUniform uniform : this.uniformOrder) {
			try {
				OptionalInt location = targetHolder.location(uniform.getName(), Type.convert(uniform.getType()));
				if (location.isPresent()) {
					locations.put(uniform, location.getAsInt());
				}
			} catch (Exception e) {
				throw new RuntimeException(uniform.getName(), e);
			}
		}
		this.locationMap.put(targetHolder, locations);
	}

	public void mapholderToPass(LocationalUniformHolder holder, Object pass) {
		locationMap.put(pass, locationMap.remove(holder));
	}


	public void update() {
		for (CachedUniform value : this.uniformOrder) {
			value.update();
		}
	}

	public void push(Object pass) {
		java.util.Map<CachedUniform, Integer> uniforms = this.locationMap.get(pass);
		if (uniforms != null) {
			uniforms.forEach(CachedUniform::pushIfChanged);
		}
	}

	/**
	 * This function will do the following:
	 * <ul>
	 *     <li>
	 *         Remove unused uniforms
	 *     </li>
	 *     <li>
	 *         TODO: Create separate push lists for each renderpass
	 *     </li>
	 *     <li>
	 *         TODO: Sort the others in the correct execution line <p/>
	 *               note: that if a `EVERY_FRAME` depends on a `EVERY_TICK`, it has to correctly now that it's
	 *               dependency hasn't updated <br/>
	 *                  suggestion: set a boolean in the `EVERY_TICK` execution line saying this is a tick
	 *                  and have it set to false in the `EVERY_FRAME`. Depending on the value, `frameDependencies` or
	 *                  `allDependencies` lists are used
	 *     </li>
	 * </ul>
	 */
	public void optimise() {

		java.util.Map<CachedUniform, Integer> dependedByCount = new java.util.HashMap<>();

		// Count the times a uniform is depended on
		for (List<CachedUniform> dependencies : this.dependsOn.values()) {
			for (CachedUniform dependency : dependencies) {
				dependedByCount.merge(dependency, 1, Integer::sum);
			}
		}

		// Count the times a pass depends on a uniform
		// ensures they wont ever be removed
		for (java.util.Map<CachedUniform, Integer> map : this.locationMap.values()) {
			for (CachedUniform cachedUniform : map.keySet()) {
				dependedByCount.merge(cachedUniform, 1, Integer::sum);
			}
		}


		Set<CachedUniform> unused = new java.util.HashSet<>();
		for (int i = this.uniformOrder.size() - 1; i >= 0; i--) {
			CachedUniform uniform = this.uniformOrder.get(i);
			if (!dependedByCount.containsKey(uniform)) {
				// not used
				unused.add(uniform);
				// remove dependencies
				List<CachedUniform> dependencies = this.dependsOn.get(uniform);
				if (dependencies != null) {
					for (CachedUniform dependency : dependencies) {
						// reduce count by 1
						dependedByCount.computeIfPresent(dependency, (key, value) -> value - 1);
					}
				}
			}
		}

		this.uniformOrder.removeAll(unused);
	}

	/** A custom variable or uniform by name (AltShaders: programs are given every one they declare, as Iris). */
	public CachedUniform custom(String name) {
		return this.variables.get(name);
	}

	/** Every standard input once, then the custom ones in dependency order. */
	public void updateAll() {
		this.inputHolder.updateAll();
		for (CachedUniform value : this.uniformOrder) {
			if (!this.inputHolder.containsKey(value.getName())) {
				value.update();
			}
		}
	}

	@Override
	public boolean hasVariable(String name) {
		return this.inputHolder.containsKey(name) || this.variables.containsKey(name);
	}

	@Override
	public Expression getVariable(String name) {
		// TODO: Make the simplify just return these ones
		final CachedUniform inputUniform = this.inputHolder.getUniform(name);
		if (inputUniform != null)
			return inputUniform;
		final CachedUniform customUniform = this.variables.get(name);
		if (customUniform != null)
			return customUniform;
		throw new RuntimeException("Unknown variable: " + name);
	}

	public static class Builder {
		final private static Map<String, Type> types = Map.of("bool", Type.Boolean, "float", Type.Float, "int", Type.Int,
			"vec2", VectorType.VEC2, "vec3", VectorType.VEC3, "vec4", VectorType.VEC4);
		final Map<String, Variable> variables = new java.util.LinkedHashMap<>();

		public void addVariable(String type, String name, String expression, boolean isUniform) {
			if (variables.containsKey(name)) {
				PackEnv.LOG.warn("Ignoring duplicated custom uniform name: " + name);
				return;
			}

			Type parsedType = types.get(type);
			if (parsedType == null) {
				PackEnv.LOG.warn("Ignoring invalid uniform type: " + type + " of " + name);
				return;
			}

			try {
				ExpressionElement ast = Parser.parse(expression, IrisOptions.options);
				variables.put(name, new Variable(parsedType, name, ast, isUniform));
			} catch (Exception e) {
				PackEnv.LOG.warn("Failed to parse custom variable/uniform " + name + " with expression " + expression, e);
			}
		}

		public CustomUniforms build(
			CustomUniformFixedInputUniformsHolder inputHolder
		) {
			return new CustomUniforms(inputHolder, this.variables);
		}

		@SafeVarargs
		public final CustomUniforms build(
			Consumer<UniformHolder>... uniforms
		) {
			CustomUniformFixedInputUniformsHolder.Builder inputs = new CustomUniformFixedInputUniformsHolder.Builder();
			for (Consumer<UniformHolder> uniform : uniforms) {
				uniform.accept(inputs);
			}
			return this.build(inputs.build());
		}

		private record Variable(Type type, String name, ExpressionElement expression, boolean uniform) {
		}


	}
}
