/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Portions of this file are derived from Iris (https://github.com/IrisShaders/Iris),
 * common/src/main/java/kroppeb/stareval/parser/Tokenizer.java at commit bff1e69c, licensed under LGPL-3.0.
 * Modified by Coredex, 2026-11-13: AltShaders' packages; Java's own collections and small value types in place of
 * Guava, fastutil and JOML; uniforms uploaded through Altrender's API.
 * See THIRD_PARTY.md and docs/provenance.md.
 */
package com.coredex.altshaders.uniform.stareval.parser;

import com.coredex.altshaders.uniform.stareval.element.ExpressionElement;
import com.coredex.altshaders.uniform.stareval.exception.ParseException;
import com.coredex.altshaders.uniform.stareval.exception.UnexpectedCharacterException;
import com.coredex.altshaders.uniform.stareval.exception.UnexpectedEndingException;

class Tokenizer {
	private Tokenizer() {
	}

	static ExpressionElement parse(String input, ParserOptions options) throws ParseException {
		return parseInternal(new StringReader(input), options);
	}

	static ExpressionElement parseInternal(StringReader input, ParserOptions options) throws ParseException {
		// parser stack
		final Parser stack = new Parser();
		ParserOptions.TokenRules tokenRules = options.getTokenRules();

		while (input.canRead()) {
			input.skipWhitespace();

			if (!input.canRead()) {
				break;
			}

			char c = input.read();

			if (tokenRules.isIdStart(c)) {
				final String id = readWhile(input, tokenRules::isIdPart);
				stack.visitId(id);
			} else if (c == '.' && stack.canReadAccess()) {
				input.skipWhitespace();

				if (input.canRead()) {
					char start = input.read();

					if (!tokenRules.isAccessStart(start)) {
						throw new UnexpectedCharacterException("a valid accessor", start, input.getCurrentIndex());
					}

					final String access = readWhile(input, tokenRules::isAccessPart);
					stack.visitAccess(access);
				} else {
					throw new UnexpectedEndingException("An expression can't end with '.'");
				}
			} else if (tokenRules.isNumberStart(c)) {
				// start parsing a number
				final String numberString = readWhile(input, tokenRules::isNumberPart);
				stack.visitNumber(numberString);
			} else if (c == '(') {
				stack.visitOpeningParenthesis();
			} else if (c == ',') {
				stack.visitComma(input.getCurrentIndex());
			} else if (c == ')') {
				stack.visitClosingParenthesis(input.getCurrentIndex());
			} else {
				if (stack.canReadBinaryOp()) {
					// maybe binary operator
					OpResolver<? extends BinaryOp> resolver = options.getBinaryOpResolver(c);

					if (resolver != null) {
						stack.visitBinaryOperator(resolver.resolve(input));
						continue;
					}
				} else {
					// maybe unary operator
					OpResolver<? extends UnaryOp> resolver = options.getUnaryOpResolver(c);

					if (resolver != null) {
						stack.visitUnaryOperator(resolver.resolve(input));
						continue;
					}
				}

				throw new UnexpectedCharacterException(c, input.getCurrentIndex());
			}
		}

		return stack.getFinal(input.getCurrentIndex());
	}

	/**
	 * The returned value includes the last value btw;
	 */
	private static String readWhile(StringReader input, CharPredicate predicate) {
		input.mark();

		while (input.canRead()) {
			if (!predicate.test(input.peek())) {
				break;
			}

			// NB: Do not skip whitespace here implicitly.
			input.skipOneCharacter();
		}

		return input.substring();
	}

	private interface CharPredicate {
		boolean test(char c);
	}
}


