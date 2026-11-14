/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altshaders.pack.properties;

/**
 * A pack's {@code variable.<type>.<name> = <expression>} or {@code uniform.<type>.<name> = <expression>} line from
 * shaders.properties, in file order (later lines may use earlier ones). Parsed and evaluated per frame in 6.5.
 *
 * @param uniform {@code uniform.} (sent to the programs) rather than {@code variable.} (only used by others)
 */
public record CustomVariable(String type, String name, String expression, boolean uniform) {
}
