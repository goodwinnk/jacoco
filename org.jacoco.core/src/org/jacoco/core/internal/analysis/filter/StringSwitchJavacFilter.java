/*******************************************************************************
 * Copyright (c) 2009, 2018 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

/**
 * Filters code that is generated by javac for a switch statement with a String.
 */
public final class StringSwitchJavacFilter implements IFilter {

	public void filter(final String className, final String superClassName,
			final Set<String> classAnnotations,
			final String sourceFileName,
			final MethodNode methodNode, final IFilterOutput output) {
		AbstractInsnNode i = methodNode.instructions.getFirst();
		while (i != null) {
			filter(i, output);
			i = i.getNext();
		}
	}

	/**
	 * javac generates two switches. First one by {@link String#hashCode()}.
	 * Number of handlers in the second switch is equal to number of handlers in
	 * source code, so it is enough to completely filter-out first switch.
	 * Handler for default case of the first switch - is the second switch.
	 */
	private void filter(final AbstractInsnNode start,
			final IFilterOutput output) {
		final LabelNode dflt;
		if (start.getOpcode() == Opcodes.LOOKUPSWITCH) {
			dflt = ((LookupSwitchInsnNode) start).dflt;
		} else if (start.getOpcode() == Opcodes.TABLESWITCH) {
			dflt = ((TableSwitchInsnNode) start).dflt;
		} else {
			return;
		}
		if (new Matcher().match(start, dflt)) {
			output.ignore(start, dflt);
		}
	}

	private static class Matcher extends AbstractMatcher {
		boolean match(final AbstractInsnNode start,
				final AbstractInsnNode secondSwitchLabel) {
			cursor = start;
			for (int i = 0; cursor != null && i < 4; i++) {
				cursor = cursor.getPrevious();
			}
			if (cursor == null || cursor.getOpcode() != Opcodes.ICONST_M1) {
				return false;
			}
			nextIsVar(Opcodes.ISTORE, "c");
			// Even if expression is not a variable, its result will be
			// precomputed before the previous two instructions:
			nextIsVar(Opcodes.ALOAD, "s");
			nextIsInvokeVirtual("java/lang/String", "hashCode");
			next();
			while (true) {
				nextIsVar(Opcodes.ALOAD, "s");
				nextIs(Opcodes.LDC);
				nextIsInvokeVirtual("java/lang/String", "equals");
				// jump to next comparison or second switch
				nextIs(Opcodes.IFEQ);
				// ICONST, BIPUSH or SIPUSH
				next();
				nextIsVar(Opcodes.ISTORE, "c");
				if (cursor == null) {
					return false;
				}
				if (cursor.getNext() == secondSwitchLabel) {
					break;
				}
				nextIs(Opcodes.GOTO);
				if (((JumpInsnNode) cursor).label != secondSwitchLabel) {
					return false;
				}
			}
			nextIsVar(Opcodes.ILOAD, "c");
			nextIs(Opcodes.TABLESWITCH);
			return cursor != null;
		}
	}

}
