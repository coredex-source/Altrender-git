/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.preprocessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A C preprocessor for shader pack sources (development.md 6.2), written for AltShaders in place of the JCPP library
 * Iris uses: the same results where packs depend on them. Object- and function-like macros (with {@code #} and
 * {@code ##}), {@code #if}/{@code #ifdef}/{@code #ifndef}/{@code #elif}/{@code #else}/{@code #endif} with
 * {@code defined} and C integer arithmetic, {@code #undef}, comments kept, line continuations. Text lines are
 * macro-expanded. In GLSL mode the taken {@code #version} and {@code #extension} lines are collected (the caller puts
 * them at the top). As with JCPP, {@code #pragma}, {@code #line}, {@code #warning} and unknown directives are dropped,
 * and problems ({@code #error}, a bad {@code #if} expression, an unclosed {@code #if}) are reported in
 * {@link #problems()} rather than failing: Iris loads such packs.
 */
public final class Cpp {

	/** A failed preprocessing ({@code #error}, unbalanced conditionals, a bad expression). */
	public static final class CppException extends RuntimeException {
		public CppException(String message) {
			super(message);
		}
	}

	private record Macro(String name, List<String> params, boolean variadic, String body) {
		boolean function() {
			return params != null;
		}
	}

	private final Map<String, Macro> macros = new HashMap<>();
	private final boolean glsl;
	private final List<String> hoisted = new ArrayList<>();
	private final List<String> problems = new ArrayList<>();
	private int lineNumber;

	/** @param glsl GLSL mode ({@code #version}/{@code #extension} collected, unknown directives kept) */
	public Cpp(boolean glsl) {
		this.glsl = glsl;
	}

	/** {@code #define name 1}, as JCPP's {@code addMacro(name)}. */
	public void define(String name) {
		define(name, "1");
	}

	public void define(String name, String value) {
		macros.put(name, new Macro(name, null, false, value));
	}

	/** The taken {@code #version} and {@code #extension} lines, in order (GLSL mode). */
	public List<String> hoisted() {
		return hoisted;
	}

	/** What went wrong ({@code #error} lines included), each with its line number. */
	public List<String> problems() {
		return problems;
	}

	private void problem(String message) {
		problems.add("line " + lineNumber + ": " + message);
	}

	/** Preprocesses {@code source}; the output keeps comments and the text lines' layout. */
	public String process(String source) {
		List<String> lines = logicalLines(source);
		StringBuilder out = new StringBuilder(source.length());
		// Conditional stack: per level, whether the current branch is taken, whether one was taken, whether the
		// enclosing context is active.
		List<boolean[]> stack = new ArrayList<>();
		boolean inComment = false;
		for (int li = 0; li < lines.size(); li++) {
			String line = lines.get(li);
			lineNumber = li + 1;
			boolean active = stack.isEmpty() || stack.get(stack.size() - 1)[0];
			String trimmed = line.stripLeading();
			if (!inComment && trimmed.startsWith("#")) {
				String directive = stripComments(trimmed.substring(1)).strip();
				String word = directive.isEmpty() ? "" : firstWord(directive);
				String rest = directive.substring(word.length()).strip();
				switch (word) {
					case "if", "ifdef", "ifndef" -> {
						boolean cond = false;
						if (active) {
							cond = switch (word) {
								case "ifdef" -> macros.containsKey(firstWord(rest));
								case "ifndef" -> !macros.containsKey(firstWord(rest));
								default -> evaluate(rest) != 0;
							};
						}
						stack.add(new boolean[] {active && cond, active && cond, active});
						out.append('\n');
						continue;
					}
					case "elif" -> {
						boolean[] top = top(stack, "#elif");
						if (top == null) {
							out.append('\n');
							continue;
						}
						boolean cond = top[2] && !top[1] && evaluate(rest) != 0;
						top[0] = cond;
						top[1] |= cond;
						out.append('\n');
						continue;
					}
					case "else" -> {
						boolean[] top = top(stack, "#else");
						if (top == null) {
							out.append('\n');
							continue;
						}
						top[0] = top[2] && !top[1];
						top[1] = true;
						out.append('\n');
						continue;
					}
					case "endif" -> {
						if (top(stack, "#endif") != null) {
							stack.remove(stack.size() - 1);
						}
						out.append('\n');
						continue;
					}
					default -> {
					}
				}
				if (!active) {
					out.append('\n');
					continue;
				}
				switch (word) {
					case "define" -> {
						try {
							defineDirective(rest);
						} catch (CppException e) {
							problem(e.getMessage());
						}
					}
					case "undef" -> macros.remove(firstWord(rest));
					case "error" -> problem("#error " + rest);
					case "version", "extension" -> {
						if (glsl) {
							hoisted.add("#" + directive);
						}
					}
					default -> {
					}
				}
				out.append('\n');
				continue;
			}
			if (!active) {
				inComment = updateComment(line, inComment);
				out.append('\n');
				continue;
			}
			// A function-like macro call may continue on the next lines: join until its parentheses close.
			StringBuilder text = new StringBuilder(line);
			while (unclosedCall(text) && li + 1 < lines.size() && !lines.get(li + 1).stripLeading().startsWith("#")) {
				text.append('\n').append(lines.get(++li));
			}
			try {
				out.append(expandText(text.toString(), inComment));
			} catch (CppException e) {
				problem(e.getMessage());
				out.append(text);
			}
			inComment = updateComment(text.toString(), inComment);
			out.append('\n');
		}
		if (!stack.isEmpty()) {
			problem("unterminated #if (" + stack.size() + " open)");
		}
		return out.toString();
	}

	private boolean[] top(List<boolean[]> stack, String what) {
		if (stack.isEmpty()) {
			problem(what + " without #if");
			return null;
		}
		return stack.get(stack.size() - 1);
	}

	/** Lines with continuations joined (a backslash at the end of a line). */
	private static List<String> logicalLines(String source) {
		String[] raw = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		List<String> lines = new ArrayList<>(raw.length);
		StringBuilder pending = null;
		for (String r : raw) {
			if (r.endsWith("\\")) {
				if (pending == null) {
					pending = new StringBuilder();
				}
				pending.append(r, 0, r.length() - 1);
				continue;
			}
			if (pending != null) {
				pending.append(r);
				lines.add(pending.toString());
				pending = null;
			} else {
				lines.add(r);
			}
		}
		if (pending != null) {
			lines.add(pending.toString());
		}
		if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
			lines.remove(lines.size() - 1); // the source's last newline ends a line, it doesn't start one
		}
		return lines;
	}

	private static String firstWord(String s) {
		int i = 0;
		while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
			i++;
		}
		return s.substring(0, i);
	}

	/** {@code s} without its comments ({@code //} to the end, block comments). */
	private static String stripComments(String s) {
		StringBuilder b = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			if (s.startsWith("//", i)) {
				break;
			}
			if (s.startsWith("/*", i)) {
				int end = s.indexOf("*/", i + 2);
				if (end < 0) {
					break;
				}
				b.append(' ');
				i = end + 2;
				continue;
			}
			b.append(s.charAt(i++));
		}
		return b.toString();
	}

	/** Whether a block comment is still open after {@code line}. */
	private static boolean updateComment(String line, boolean inComment) {
		int i = 0;
		while (i < line.length()) {
			if (inComment) {
				int end = line.indexOf("*/", i);
				if (end < 0) {
					return true;
				}
				inComment = false;
				i = end + 2;
			} else if (line.startsWith("//", i)) {
				return false;
			} else if (line.startsWith("/*", i)) {
				inComment = true;
				i += 2;
			} else if (line.charAt(i) == '"') {
				int end = line.indexOf('"', i + 1);
				i = end < 0 ? line.length() : end + 1;
			} else {
				i++;
			}
		}
		return inComment;
	}

	private void defineDirective(String rest) {
		String name = firstWord(rest);
		if (name.isEmpty()) {
			throw new CppException("#define without a name");
		}
		String after = rest.substring(name.length());
		if (after.startsWith("(")) {
			int close = after.indexOf(')');
			if (close < 0) {
				throw new CppException("#define " + name + ": unclosed parameter list");
			}
			List<String> params = new ArrayList<>();
			boolean variadic = false;
			for (String p : after.substring(1, close).split(",")) {
				String t = p.strip();
				if (t.equals("...")) {
					variadic = true;
				} else if (!t.isEmpty()) {
					params.add(t);
				}
			}
			macros.put(name, new Macro(name, params, variadic, after.substring(close + 1).strip()));
		} else {
			macros.put(name, new Macro(name, null, false, after.strip()));
		}
	}

	// ---- macro expansion of text ----

	/** Whether {@code text} ends inside a function-like macro call's argument list. */
	private boolean unclosedCall(CharSequence text) {
		String s = text.toString();
		int i = 0;
		int depth = 0;
		boolean inCall = false;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (s.startsWith("//", i)) {
				break;
			}
			if (s.startsWith("/*", i)) {
				int end = s.indexOf("*/", i + 2);
				if (end < 0) {
					return false;
				}
				i = end + 2;
				continue;
			}
			if (Character.isLetter(c) || c == '_') {
				int j = i;
				while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
					j++;
				}
				Macro m = macros.get(s.substring(i, j));
				if (!inCall && m != null && m.function()) {
					int k = j;
					while (k < s.length() && Character.isWhitespace(s.charAt(k))) {
						k++;
					}
					if (k < s.length() && s.charAt(k) == '(') {
						inCall = true;
						depth = 0;
					}
				}
				i = j;
				continue;
			}
			if (inCall) {
				if (c == '(') {
					depth++;
				} else if (c == ')' && --depth == 0) {
					inCall = false;
				}
			}
			i++;
		}
		return inCall && depth > 0;
	}

	/** Expands the macros in a text line, leaving comments (and the part of a block comment still open) as they are. */
	private String expandText(String text, boolean inComment) {
		StringBuilder out = new StringBuilder(text.length());
		StringBuilder code = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			if (inComment) {
				int end = text.indexOf("*/", i);
				if (end < 0) {
					out.append(text, i, text.length());
					return out.toString();
				}
				out.append(text, i, end + 2);
				i = end + 2;
				inComment = false;
				continue;
			}
			if (text.startsWith("//", i)) {
				out.append(expand(code.toString(), new HashSet<>()));
				code.setLength(0);
				out.append(text, i, text.length());
				return out.toString();
			}
			if (text.startsWith("/*", i)) {
				out.append(expand(code.toString(), new HashSet<>()));
				code.setLength(0);
				inComment = true;
				out.append("/*");
				i += 2;
				continue;
			}
			code.append(text.charAt(i++));
		}
		out.append(expand(code.toString(), new HashSet<>()));
		return out.toString();
	}

	/** Expands macros in code (no comments), not re-expanding the macros in {@code painted}. */
	private String expand(String input, Set<String> painted) {
		if (macros.isEmpty() || input.isEmpty()) {
			return input;
		}
		String code = input;
		StringBuilder out = new StringBuilder(code.length());
		int i = 0;
		int n = code.length();
		while (i < n) {
			char c = code.charAt(i);
			if (c == '"') {
				int end = code.indexOf('"', i + 1);
				end = end < 0 ? n : end + 1;
				out.append(code, i, end);
				i = end;
				continue;
			}
			if (Character.isDigit(c) || c == '.' && i + 1 < n && Character.isDigit(code.charAt(i + 1))) {
				int j = i + 1;
				while (j < n && (Character.isLetterOrDigit(code.charAt(j)) || code.charAt(j) == '.' || code.charAt(j) == '_'
						|| (code.charAt(j) == '+' || code.charAt(j) == '-')
						&& (code.charAt(j - 1) == 'e' || code.charAt(j - 1) == 'E'))) {
					j++;
				}
				out.append(code, i, j);
				i = j;
				continue;
			}
			if (!(Character.isLetter(c) || c == '_')) {
				out.append(c);
				i++;
				continue;
			}
			int j = i;
			while (j < n && (Character.isLetterOrDigit(code.charAt(j)) || code.charAt(j) == '_')) {
				j++;
			}
			String name = code.substring(i, j);
			Macro m = macros.get(name);
			if (m == null || painted.contains(name)) {
				out.append(name);
				i = j;
				continue;
			}
			if (!m.function()) {
				Set<String> p = new HashSet<>(painted);
				p.add(name);
				String result = expand(m.body(), p);
				String tail = rescanTail(result, code, j, painted);
				if (tail != null) {
					// The expansion ends in a function-like macro's name and the text goes on with "(": C rescans
					// the two together, so the call happens.
					out.append(result, 0, result.length() - tail.length());
					code = tail + code.substring(j);
					n = code.length();
					i = 0;
					continue;
				}
				out.append(result);
				i = j;
				continue;
			}
			int k = j;
			while (k < n && Character.isWhitespace(code.charAt(k))) {
				k++;
			}
			if (k >= n || code.charAt(k) != '(') {
				out.append(name); // a function-like macro's name without a call
				i = j;
				continue;
			}
			List<String> args = new ArrayList<>();
			int depth = 0;
			int start = k + 1;
			int p = k;
			for (; p < n; p++) {
				char ch = code.charAt(p);
				if (ch == '(') {
					depth++;
				} else if (ch == ')') {
					if (--depth == 0) {
						args.add(code.substring(start, p));
						break;
					}
				} else if (ch == ',' && depth == 1) {
					args.add(code.substring(start, p));
					start = p + 1;
				}
			}
			if (p >= n) {
				throw new CppException("unterminated call of macro " + name);
			}
			if (args.size() == 1 && args.get(0).isBlank() && m.params().isEmpty()) {
				args.clear();
			}
			Set<String> pt = new HashSet<>(painted);
			pt.add(name);
			String result = expand(substitute(m, args, painted), pt);
			String tail = rescanTail(result, code, p + 1, painted);
			if (tail != null) {
				out.append(result, 0, result.length() - tail.length());
				code = tail + code.substring(p + 1);
				n = code.length();
				i = 0;
				continue;
			}
			out.append(result);
			i = p + 1;
		}
		return out.toString();
	}

	/**
	 * The function-like macro name {@code result} ends with, if {@code code} continues at {@code from} with a
	 * {@code (} (after whitespace) and the macro isn't painted; else null.
	 */
	private String rescanTail(String result, String code, int from, Set<String> painted) {
		int end = result.length();
		while (end > 0 && Character.isWhitespace(result.charAt(end - 1))) {
			end--;
		}
		int start = end;
		while (start > 0 && isIdent(result.charAt(start - 1))) {
			start--;
		}
		if (start == end || Character.isDigit(result.charAt(start))) {
			return null;
		}
		String name = result.substring(start, end);
		Macro m = macros.get(name);
		if (m == null || !m.function() || painted.contains(name)) {
			return null;
		}
		int k = skipSpace(code, from);
		return k < code.length() && code.charAt(k) == '(' ? result.substring(start) : null;
	}

	/** The macro's body with its parameters replaced ({@code #x} quoted, {@code a ## b} pasted). */
	private String substitute(Macro m, List<String> args, Set<String> painted) {
		Map<String, String> raw = new HashMap<>();
		for (int i = 0; i < m.params().size(); i++) {
			raw.put(m.params().get(i), i < args.size() ? args.get(i).strip() : "");
		}
		if (m.variadic()) {
			List<String> rest = args.size() > m.params().size() ? args.subList(m.params().size(), args.size())
					: List.of();
			raw.put("__VA_ARGS__", String.join(",", rest).strip());
		}
		String body = m.body();
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '#' && i + 1 < body.length() && body.charAt(i + 1) == '#') {
				// Paste: drop the whitespace around ##; the neighbours are taken unexpanded.
				while (out.length() > 0 && Character.isWhitespace(out.charAt(out.length() - 1))) {
					out.setLength(out.length() - 1);
				}
				i += 2;
				while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
					i++;
				}
				int j = i;
				while (j < body.length() && (Character.isLetterOrDigit(body.charAt(j)) || body.charAt(j) == '_')) {
					j++;
				}
				String id = body.substring(i, j);
				out.append(raw.getOrDefault(id, id));
				i = j;
				continue;
			}
			if (c == '#') {
				int j = i + 1;
				while (j < body.length() && Character.isWhitespace(body.charAt(j))) {
					j++;
				}
				int k = j;
				while (k < body.length() && (Character.isLetterOrDigit(body.charAt(k)) || body.charAt(k) == '_')) {
					k++;
				}
				String id = body.substring(j, k);
				if (raw.containsKey(id)) {
					out.append('"').append(raw.get(id).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
					i = k;
					continue;
				}
			}
			if (Character.isLetter(c) || c == '_') {
				int j = i;
				while (j < body.length() && (Character.isLetterOrDigit(body.charAt(j)) || body.charAt(j) == '_')) {
					j++;
				}
				String id = body.substring(i, j);
				if (raw.containsKey(id)) {
					// An argument is expanded before it's put in, unless it's next to ##.
					boolean pasteAfter = body.startsWith("##", skipSpace(body, j));
					out.append(pasteAfter ? raw.get(id) : expand(raw.get(id), painted));
				} else {
					out.append(id);
				}
				i = j;
				continue;
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	private static int skipSpace(String s, int i) {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return i;
	}

	// ---- #if expressions ----

	/** Evaluates a {@code #if} expression: {@code defined} first, then macros, then C integer arithmetic. */
	long evaluate(String expression) {
		String e = stripComments(expression);
		// defined X / defined(X) before expansion
		StringBuilder b = new StringBuilder();
		int i = 0;
		while (i < e.length()) {
			if (e.startsWith("defined", i) && (i == 0 || !isIdent(e.charAt(i - 1)))
					&& (i + 7 >= e.length() || !isIdent(e.charAt(i + 7)))) {
				int j = skipSpace(e, i + 7);
				boolean paren = j < e.length() && e.charAt(j) == '(';
				if (paren) {
					j = skipSpace(e, j + 1);
				}
				int k = j;
				while (k < e.length() && isIdent(e.charAt(k))) {
					k++;
				}
				String name = e.substring(j, k);
				if (paren) {
					k = skipSpace(e, k);
					if (k < e.length() && e.charAt(k) == ')') {
						k++;
					}
				}
				b.append(macros.containsKey(name) ? " 1 " : " 0 ");
				i = k;
				continue;
			}
			b.append(e.charAt(i++));
		}
		try {
			return new Expr(expand(b.toString(), new HashSet<>())).parse();
		} catch (CppException ex) {
			problem(ex.getMessage());
			return 0;
		}
	}

	private static boolean isIdent(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/** A recursive-descent evaluator for C's integer expressions; identifiers left after expansion are 0. */
	private static final class Expr {
		private final String s;
		private int i;

		Expr(String s) {
			this.s = s;
		}

		long parse() {
			long v = ternary();
			skip();
			if (i < s.length()) {
				throw new CppException("bad #if expression near '" + s.substring(i) + "' in: " + s.strip());
			}
			return v;
		}

		private void skip() {
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
				i++;
			}
		}

		private boolean eat(String op) {
			skip();
			if (s.startsWith(op, i)) {
				// don't take "<" from "<<" or "<=", "&" from "&&", "|" from "||", "=" alone
				if (op.length() == 1 && i + 1 < s.length()) {
					char next = s.charAt(i + 1);
					char c = op.charAt(0);
					if ((c == '<' || c == '>') && (next == c || next == '=')
							|| (c == '&' || c == '|') && next == c
							|| c == '!' && next == '=') {
						return false;
					}
				}
				i += op.length();
				return true;
			}
			return false;
		}

		private long ternary() {
			long c = or();
			if (eat("?")) {
				long a = ternary();
				if (!eat(":")) {
					throw new CppException("':' expected in #if expression: " + s.strip());
				}
				long b = ternary();
				return c != 0 ? a : b;
			}
			return c;
		}

		private long or() {
			long v = and();
			while (eat("||")) {
				long r = and();
				v = v != 0 || r != 0 ? 1 : 0;
			}
			return v;
		}

		private long and() {
			long v = bitOr();
			while (eat("&&")) {
				long r = bitOr();
				v = v != 0 && r != 0 ? 1 : 0;
			}
			return v;
		}

		private long bitOr() {
			long v = bitXor();
			while (eat("|")) {
				v |= bitXor();
			}
			return v;
		}

		private long bitXor() {
			long v = bitAnd();
			while (eat("^")) {
				v ^= bitAnd();
			}
			return v;
		}

		private long bitAnd() {
			long v = equality();
			while (eat("&")) {
				v &= equality();
			}
			return v;
		}

		private long equality() {
			long v = relational();
			while (true) {
				if (eat("==")) {
					v = v == relational() ? 1 : 0;
				} else if (eat("!=")) {
					v = v != relational() ? 1 : 0;
				} else {
					return v;
				}
			}
		}

		private long relational() {
			long v = shift();
			while (true) {
				if (eat("<=")) {
					v = v <= shift() ? 1 : 0;
				} else if (eat(">=")) {
					v = v >= shift() ? 1 : 0;
				} else if (eat("<")) {
					v = v < shift() ? 1 : 0;
				} else if (eat(">")) {
					v = v > shift() ? 1 : 0;
				} else {
					return v;
				}
			}
		}

		private long shift() {
			long v = additive();
			while (true) {
				if (eat("<<")) {
					v <<= additive();
				} else if (eat(">>")) {
					v >>= additive();
				} else {
					return v;
				}
			}
		}

		private long additive() {
			long v = multiplicative();
			while (true) {
				if (eat("+")) {
					v += multiplicative();
				} else if (eat("-")) {
					v -= multiplicative();
				} else {
					return v;
				}
			}
		}

		private long multiplicative() {
			long v = unary();
			while (true) {
				if (eat("*")) {
					v *= unary();
				} else if (eat("/")) {
					long d = unary();
					v = d == 0 ? 0 : v / d;
				} else if (eat("%")) {
					long d = unary();
					v = d == 0 ? 0 : v % d;
				} else {
					return v;
				}
			}
		}

		private long unary() {
			if (eat("!")) {
				return unary() == 0 ? 1 : 0;
			}
			if (eat("-")) {
				return -unary();
			}
			if (eat("+")) {
				return unary();
			}
			if (eat("~")) {
				return ~unary();
			}
			return primary();
		}

		private long primary() {
			skip();
			if (eat("(")) {
				long v = ternary();
				if (!eat(")")) {
					throw new CppException("')' expected in #if expression: " + s.strip());
				}
				return v;
			}
			int start = i;
			if (i < s.length() && Character.isDigit(s.charAt(i))) {
				while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.')) {
					i++;
				}
				return number(s.substring(start, i));
			}
			if (i < s.length() && (Character.isLetter(s.charAt(i)) || s.charAt(i) == '_')) {
				while (i < s.length() && isIdent(s.charAt(i))) {
					i++;
				}
				return 0; // an identifier that isn't a macro
			}
			throw new CppException("bad #if expression near '" + s.substring(i) + "' in: " + s.strip());
		}

		private static long number(String t) {
			String n = t.replaceAll("[uUlL]+$", "");
			try {
				if (n.startsWith("0x") || n.startsWith("0X")) {
					return Long.parseLong(n.substring(2), 16);
				}
				if (n.contains(".") || n.contains("e") || n.contains("E")) {
					return (long) Double.parseDouble(n.replaceAll("[fF]$", "")); // not C, but lenient as drivers are
				}
				if (n.length() > 1 && n.startsWith("0")) {
					return Long.parseLong(n.substring(1), 8);
				}
				return Long.parseLong(n);
			} catch (NumberFormatException e) {
				throw new CppException("bad number in #if: " + t);
			}
		}
	}
}
