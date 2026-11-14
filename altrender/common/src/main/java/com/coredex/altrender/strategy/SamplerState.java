/*
 * Copyright (C) 2026 Coredex
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.coredex.altrender.strategy;

/**
 * Sampler parameters in our own terms.
 *
 * @param linearMin    linear filtering within a mip level when minifying (mip levels are always blended linearly)
 * @param maxAnisotropy 1 = off
 * @param maxLod       {@code NaN} = driver default
 */
public record SamplerState(boolean repeatU, boolean repeatV, boolean linearMin, boolean linearMag, int maxAnisotropy,
						   double maxLod) {
}
