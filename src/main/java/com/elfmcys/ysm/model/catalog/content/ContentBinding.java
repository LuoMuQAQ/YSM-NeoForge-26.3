package com.elfmcys.ysm.model.catalog.content;

import com.elfmcys.ysm.model.domain.Hash256;

/** Catalog-held strong reference to one current content object. */
public interface ContentBinding {
    Hash256 modelId();

    ModelContent content();
}
