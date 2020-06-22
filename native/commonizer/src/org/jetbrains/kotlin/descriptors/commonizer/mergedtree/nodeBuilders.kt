/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import org.jetbrains.kotlin.descriptors.commonizer.cir.*
import org.jetbrains.kotlin.descriptors.commonizer.cir.impl.CirClassRecursionMarker
import org.jetbrains.kotlin.descriptors.commonizer.cir.impl.CirClassifierRecursionMarker
import org.jetbrains.kotlin.descriptors.commonizer.core.*
import org.jetbrains.kotlin.descriptors.commonizer.utils.CommonizedGroup
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.NullableLazyValue
import org.jetbrains.kotlin.storage.StorageManager

internal fun buildRootNode(
    storageManager: StorageManager,
    size: Int,
): CirRootNode = buildNode(
    storageManager = storageManager,
    size = size,
    commonizerProducer = ::RootCommonizer,
    nodeProducer = ::CirRootNode
)

internal fun buildModuleNode(
    storageManager: StorageManager,
    size: Int
): CirModuleNode = buildNode(
    storageManager = storageManager,
    size = size,
    commonizerProducer = ::ModuleCommonizer,
    nodeProducer = ::CirModuleNode
)

internal fun buildPackageNode(
    storageManager: StorageManager,
    size: Int,
    fqName: FqName,
    moduleName: Name
): CirPackageNode = buildNode(
    storageManager = storageManager,
    size = size,
    commonizerProducer = ::PackageCommonizer,
    nodeProducer = { targetDeclarations, commonDeclaration ->
        CirPackageNode(targetDeclarations, commonDeclaration, fqName, moduleName)
    }
)

internal fun buildPropertyNode(
    storageManager: StorageManager,
    size: Int,
    classifiersCache: CirCommonizedClassifiersCache,
    parentCommonDeclaration: NullableLazyValue<*>?
): CirPropertyNode = buildNode(
    storageManager = storageManager,
    size = size,
    parentCommonDeclaration = parentCommonDeclaration,
    commonizerProducer = { PropertyCommonizer(classifiersCache) },
    nodeProducer = ::CirPropertyNode
)

internal fun buildFunctionNode(
    storageManager: StorageManager,
    size: Int,
    classifiersCache: CirCommonizedClassifiersCache,
    parentCommonDeclaration: NullableLazyValue<*>?
): CirFunctionNode = buildNode(
    storageManager = storageManager,
    size = size,
    parentCommonDeclaration = parentCommonDeclaration,
    commonizerProducer = { FunctionCommonizer(classifiersCache) },
    nodeProducer = ::CirFunctionNode
)

internal fun buildClassNode(
    storageManager: StorageManager,
    size: Int,
    classifiersCache: CirCommonizedClassifiersCache,
    nodesCacheW: CirClassifierNodesCacheW,
    parentCommonDeclaration: NullableLazyValue<*>?,
    fqName: FqName
): CirClassNode = buildNode(
    storageManager = storageManager,
    size = size,
    parentCommonDeclaration = parentCommonDeclaration,
    commonizerProducer = { ClassCommonizer(classifiersCache) },
    recursionMarker = CirClassRecursionMarker,
    nodeProducer = { targetDeclarations, commonDeclaration ->
        val node = CirClassNode(targetDeclarations, commonDeclaration, fqName)
        nodesCacheW.addClassNode(node)
        node
    }
)

internal fun buildClassConstructorNode(
    storageManager: StorageManager,
    size: Int,
    classifiersCache: CirCommonizedClassifiersCache,
    parentCommonDeclaration: NullableLazyValue<*>?
): CirClassConstructorNode = buildNode(
    storageManager = storageManager,
    size = size,
    parentCommonDeclaration = parentCommonDeclaration,
    commonizerProducer = { ClassConstructorCommonizer(classifiersCache) },
    nodeProducer = ::CirClassConstructorNode
)

internal fun buildTypeAliasNode(
    storageManager: StorageManager,
    size: Int,
    classifiersCache: CirCommonizedClassifiersCache,
    nodesCacheW: CirClassifierNodesCacheW,
    fqName: FqName
): CirTypeAliasNode = buildNode(
    storageManager = storageManager,
    size = size,
    commonizerProducer = { TypeAliasCommonizer(classifiersCache) },
    recursionMarker = CirClassifierRecursionMarker,
    nodeProducer = { targetDeclarations, commonDeclaration ->
        val node = CirTypeAliasNode(targetDeclarations, commonDeclaration, fqName)
        nodesCacheW.addTypeAliasNode(node)
        node
    }
)

private fun <T : CirDeclaration, R : CirDeclaration, N : CirNode<T, R>> buildNode(
    storageManager: StorageManager,
    size: Int,
    parentCommonDeclaration: NullableLazyValue<*>? = null,
    commonizerProducer: () -> Commonizer<T, R>,
    recursionMarker: R? = null,
    nodeProducer: (CommonizedGroup<T>, NullableLazyValue<R>) -> N
): N {
    val targetDeclarations = CommonizedGroup<T>(size)

    val commonComputable = { commonize(parentCommonDeclaration, targetDeclarations, commonizerProducer()) }

    val commonLazyValue = if (recursionMarker != null)
        storageManager.createRecursionTolerantNullableLazyValue(commonComputable, recursionMarker)
    else
        storageManager.createNullableLazyValue(commonComputable)

    return nodeProducer(targetDeclarations, commonLazyValue)
}

internal fun <T : Any, R> commonize(
    targetDeclarations: CommonizedGroup<T>,
    commonizer: Commonizer<T, R>
): R? {
    for (targetDeclaration in targetDeclarations) {
        if (targetDeclaration == null || !commonizer.commonizeWith(targetDeclaration))
            return null
    }

    return commonizer.result
}

@Suppress("NOTHING_TO_INLINE")
private inline fun <T : Any, R> commonize(
    parentCommonDeclaration: NullableLazyValue<*>?,
    targetDeclarations: CommonizedGroup<T>,
    commonizer: Commonizer<T, R>
): R? {
    if (parentCommonDeclaration != null && parentCommonDeclaration.invoke() == null) {
        // don't commonize declaration if it's parent failed to commonize
        return null
    }

    return commonize(targetDeclarations, commonizer)
}
