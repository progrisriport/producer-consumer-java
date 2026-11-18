package producerconsumer.model;

import java.util.Objects;

/**
 * Elemento prodotto da un producer e consumato da un consumer.
 * Immutabile: può essere condiviso tra thread senza sincronizzazione.
 */
public final class Item {

    /**
     * Poison pill: segnala al consumer che non arriveranno altri item.
     * Ha valori impossibili per un item reale (-1).
     */
    public static final Item POISON = new Item(-1, -1);

    private final int id;
    private final int producerId;

    /**
     * @param id         identificativo unico dell'item, generato da un contatore globale
     * @param producerId identificativo del producer che ha creato l'item
     */
    public Item(int id, int producerId) {
        this.id = id;
        this.producerId = producerId;
    }

    /**
     * @return identificativo unico dell'item
     */
    public int getId() {
        return id;
    }

    /**
     * @return identificativo del producer che ha creato l'item
     */
    public int getProducerId() {
        return producerId;
    }

    /**
     * Confronto per identità con {@link #POISON}, non per valore.
     *
     * @return true se questo item è la poison pill
     */
    public boolean isPoison() {
        return this == POISON;
    }

    /**
     * Due item sono uguali se hanno stesso id e stesso producerId.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Item)) {
            return false;
        }
        Item other = (Item) o;
        return id == other.id && producerId == other.producerId;
    }

    /**
     * Coerente con {@link #equals(Object)}: usa gli stessi campi.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, producerId);
    }

    @Override
    public String toString() {
        return "Item[id=" + id + ", producerId=" + producerId + "]";
    }
}
