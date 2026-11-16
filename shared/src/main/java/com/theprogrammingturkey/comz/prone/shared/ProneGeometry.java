package com.theprogrammingturkey.comz.prone.shared;

/** Common model geometry for the client camera and server-side weapon origin. */
public final class ProneGeometry
{
	/* DIVE / PRONE pose — low tactical slide (belly on the ground, arms reaching
	 * down/forward, legs trailing back, head forward).
	 *
	 * The whole avatar is leaned belly-down by LEAN_DEGREES about the hip and then
	 * rested at HIP_HEIGHT above the floor, so the belly sits on the ground.
	 *
	 * LEAN_DEGREES: 80° ≈ a body 10° above horizontal — nearly flat, belly on the dirt.
	 * HIP_HEIGHT:   final hip height above the ground when fully prone (belly ~0.05 up). */
	public static final float HIP_PIVOT     = 0.85f;  // hip height above the feet when standing (block space)
	public static final float HIP_HEIGHT    = 0.20f;  // hip height above the ground when fully prone (belly ~0.05 above the floor)
	public static final float LEAN_DEGREES  = 80f;    // belly-down lean; body 10° above horizontal (low tactical slide)

	/* --- Tactical-crawl limb constants (applied on top of the body lean) ---
	 *
	 * The whole avatar is rotated belly-down (LEAN_DEGREES) in ProneDiveMixin and the
	 * limbs below are posed relative to that already-rotated frame. A limb's xRot is
	 * applied about its own pivot; because the body lean is a rotation ABOUT X, the X
	 * axis itself is unchanged, so these signs hold regardless of the lean size.
	 *
	 * Verified with a world-space pose calc for the BELLY-DOWN frame (XN, theta=LEAN):
	 *   arm world direction = (0, -cos(a+theta), -sin(a+theta)),  a = arm xRot.
	 *   a ~ -2.1 rad -> arm reaches DOWN & FORWARD  (hands planted ahead).
	 *   a ~ +1.7 rad -> arm kicks UP (superman) — WRONG; that was the old back-down frame.
	 * Legs are symmetric about the spine, so they trail back unchanged at 0. */
	public static final float DIVE_ARM_ARM_PITCH  = -2.10f; // arms down & forward (hands on the dirt ahead)
	public static final float DIVE_ARM_STROKE     =  0.30f; // ± arm stroke amplitude
	public static final float DIVE_LEG_KNEE_PITCH =  0.00f; // legs trail back (vanilla single-piece leg, no knee joint)
	public static final float DIVE_LEG_STROKE     = -0.35f; // ± leg drive amplitude (diagonal scull)
	public static final float DIVE_KNEE_BEND      =  0.00f; // no shin fold (vanilla legs have no knee part)

	/*
	 * CROUCH (deep squat) pose. Distinct from the dive-prone:
	 *   - body leans forward modestly (vs. 78° for the dive)
	 *   - hips drop some (knees stay planted on the ground)
	 *   - limbs use the same pitch conventions as the dive:
	 *     ARM upper.xRot negative forward (dive ≈ -1.52)
	 *     ARM lower.xRot negative to fold the elbow in (dive ≈ -1.30)
	 *     LEG upper.xRot near 0 → slightly forward (dive parent ≈ -0.08 .. -0.63)
	 *     LEG lower.xRot positive to fold the shin (dive 0.16 → 0.86)
	 *
	 * All values are scaled by {@code DiveState.crouchBlend()} at render time so
	 * the stance eases in/out. Values live here, not in the model, so tuning does
	 * not require touching the render code.
	 */
	public static final float CROUCH_BODY_LEAN  = 0.30f;   // ~17° forward lean (upright torso, the proven non-cramped crouch)
	public static final float CROUCH_HIP_DROP   = 0.38f;   // hip height 0.85 → 0.47 (a touch deeper than the original 0.30; matches the leg reach)
	public static final float CROUCH_HEAD_PITCH = 0.18f;   // less than the body lean: face sits slightly ABOVE level (~7° up)

	/* VANILLA SNEAK (Shift) — subtle body lowering, NOT the deep crouch.
	 * Matches vanilla's hitbox reduction (~0.125 blocks) with a slight leg bend. */
	public static final float SNEAK_HIP_DROP    = 0.15f;   // subtle drop (vs 0.38 for deep crouch)
	public static final float SNEAK_BODY_LEAN   = 0.08f;   // minimal lean — keeps torso visually connected to legs (no hip gap)
	public static final float SNEAK_THIGH       = -0.55f;  // moderate bend (vs -0.95 for crouch) — keeps legs under the body
	public static final float SNEAK_SHIN        =  1.00f;  // moderate fold (vs 2.15 for crouch) — knees drawn up, no separation
	public static final float SNEAK_ARM_PITCH   = -0.40f;  // relaxed forward (vs -0.70 for crouch)
	public static final float SNEAK_ARM_ELBOW   = -0.50f;  // light fold (vs -1.05 for crouch)

	/* Crouch arms: forward with light elbow fold (readable as "standing low, ready"). */
	public static final float CROUCH_ARM_XROT  = -0.95f;
	public static final float CROUCH_ARM_ZROT  =  0.20f;
	public static final float CROUCH_ARM_LOWER = -0.35f;

	/* Crouch legs: thighs near-vertical, shins folded under the hips (squatted). */
	public static final float CROUCH_LEG_XROT  = -0.20f;
	public static final float CROUCH_LEG_ZROT  =  0.06f;
	public static final float CROUCH_LEG_LOWER =  0.95f;
	private ProneGeometry() {}

	public static double eyeHeight(float body, float head)
	{
		// The hip eases from standing height down to the prone resting height as
		// body() goes 0->1, and the face is counter-rotated level, so the eye sits a
		// little above the hip line. (Verified: prone head ~0.33 above the ground.)
		double hipHeight = HIP_PIVOT - (HIP_PIVOT - HIP_HEIGHT) * body;
		return Math.max(0.10, hipHeight + 0.12);
	}

	/**
	 * Crouch (deep squat) target eye height above the feet. Lands ~1.10, between
	 * the standing eye (~1.62) and the flat dive-prone (~0.6).
	 */
	public static double crouchEyeHeight()
	{
		double hipHeight = HIP_PIVOT - CROUCH_HIP_DROP;
		double headY = hipHeight + 0.65;
		double eyeY = headY - 0.22 * Math.sin(CROUCH_BODY_LEAN);
		return Math.max(0.4, eyeY);
	}

	/**
	 * Flat prone (held, settled) target eye height above the feet — the curve at
	 * full body lean with the held head counter-rotation. Keep in sync with
	 * {@code DiveState.PRONE_HEAD} (the face-lift multiplier on the head).
	 */
	public static double proneEyeHeight()
	{
		return eyeHeight(1.0f, -1.5f);
	}

	/** Standing eye height reference (body at rest) the camera blends up from. */
	public static double standingEyeHeight()
	{
		return eyeHeight(0, 0);
	}

	public static float[] entryPose(long millis)
	{
		float t = Math.max(0, Math.min(1, millis / 400f));
		if(t < 1)
		{
			float u = t - 1;
			return new float[] {u * u * (2.45f * u + 1.45f) + 1, smooth((t - 0.25f) / 0.75f)};
		}
		float settle = smooth((millis - 400) / 400f);
		return new float[] {1, 1 - 2.5f * settle};
	}

	private static float smooth(float t)
	{
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
}
