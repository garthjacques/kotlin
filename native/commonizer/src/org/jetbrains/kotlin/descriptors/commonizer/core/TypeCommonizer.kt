/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.core

import org.jetbrains.kotlin.descriptors.commonizer.cir.CirFlexibleType
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirSimpleType
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirSimpleTypeKind
import org.jetbrains.kotlin.descriptors.commonizer.cir.CirType
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirCommonizedClassifiersCache
import org.jetbrains.kotlin.descriptors.commonizer.utils.isUnderStandardKotlinPackages
import org.jetbrains.kotlin.types.AbstractStrictEqualityTypeChecker

class TypeCommonizer(private val classifiersCache: CirCommonizedClassifiersCache) : AbstractStandardCommonizer<CirType, CirType>() {
    private lateinit var temp: CirType

    override fun commonizationResult() = temp

    override fun initialize(first: CirType) {
        temp = first
    }

    override fun doCommonizeWith(next: CirType) = areTypesEqual(classifiersCache, temp, next)
}

/**
 * See also [AbstractStrictEqualityTypeChecker].
 */
@Suppress("IntroduceWhenSubject")
internal fun areTypesEqual(classifiersCache: CirCommonizedClassifiersCache, a: CirType, b: CirType): Boolean = when {
    a is CirSimpleType -> (b is CirSimpleType) && areSimpleTypesEqual(classifiersCache, a, b)
    a is CirFlexibleType -> (b is CirFlexibleType)
            && areSimpleTypesEqual(classifiersCache, a.lowerBound, b.lowerBound)
            && areSimpleTypesEqual(classifiersCache, a.upperBound, b.upperBound)
    else -> false
}

private fun areSimpleTypesEqual(classifiersCache: CirCommonizedClassifiersCache, a: CirSimpleType, b: CirSimpleType): Boolean {
    if (a !== b
        && (a.arguments.size != b.arguments.size
                || a.isMarkedNullable != b.isMarkedNullable
                || a.isDefinitelyNotNullType != b.isDefinitelyNotNullType
                || a.fqName != b.fqName)
    ) {
        return false
    }

    fun isClassOrTypeAliasUnderStandardKotlinPackages() =
        // N.B. only for descriptors that represent classes or type aliases, but not type parameters!
        a.isClassOrTypeAlias && b.isClassOrTypeAlias && a.fqName.isUnderStandardKotlinPackages

    fun descriptorsCanBeCommonizedThemselves() =
        a.kind == b.kind && when (a.kind) {
            CirSimpleTypeKind.CLASS -> classifiersCache.hasCommonizedClass(a.fqName)
            CirSimpleTypeKind.TYPE_ALIAS -> classifiersCache.hasCommonizedTypeAlias(a.fqName)
            CirSimpleTypeKind.TYPE_PARAMETER -> {
                // Real type parameter commonization is performed in TypeParameterCommonizer.
                // Here it is enough to check that FQ names are equal (which is already done above).
                true
            }
        }

    val descriptorsCanBeCommonized =
        /* either class or type alias from Kotlin stdlib */ isClassOrTypeAliasUnderStandardKotlinPackages()
            || /* or descriptors themselves can be commonized */ descriptorsCanBeCommonizedThemselves()

    if (!descriptorsCanBeCommonized)
        return false

    // N.B. both lists of arguments are already known to be of the same size
    for (i in a.arguments.indices) {
        val aArg = a.arguments[i]
        val bArg = b.arguments[i]

        if (aArg.isStarProjection != bArg.isStarProjection)
            return false

        if (!aArg.isStarProjection) {
            if (aArg.projectionKind != bArg.projectionKind)
                return false

            if (!areTypesEqual(classifiersCache, aArg.type, bArg.type))
                return false
        }
    }

    return true
}
