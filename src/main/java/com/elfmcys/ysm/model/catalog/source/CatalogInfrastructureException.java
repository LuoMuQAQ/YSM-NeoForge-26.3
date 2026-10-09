package com.elfmcys.ysm.model.catalog.source;

public final class CatalogInfrastructureException extends CatalogBuildException {
    public CatalogInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogInfrastructureException(String message) {
        super(message);
    }
}
