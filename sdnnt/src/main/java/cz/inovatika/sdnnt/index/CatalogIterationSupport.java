package cz.inovatika.sdnnt.index;

/**
 * Generic iteration support
 */
public class CatalogIterationSupport extends IterationSupport{

    public static final String CATALOG_INDEX = "catalog";

    protected String getCollection() {
        return CATALOG_INDEX;
    }
}
