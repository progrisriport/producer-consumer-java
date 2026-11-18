package producerconsumer.buffer;

import producerconsumer.model.Item;

/**
 * Buffer a capacità limitata condiviso tra producer e consumer.
 * Ogni implementazione usa una strategia di sincronizzazione diversa (o nessuna).
 */
public interface Buffer {

    /**
     * Inserisce un item nel buffer.
     * Se il buffer è pieno, il thread attende finché si libera un posto.
     *
     * @throws NullPointerException se item è null (altrimenti take potrebbe restituire null)
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     */
    void put(Item item) throws InterruptedException;

    /**
     * Rimuove e restituisce il prossimo item dal buffer.
     * Se il buffer è vuoto, il thread attende finché arriva un item.
     * Non restituisce mai null.
     *
     * @throws InterruptedException se il thread viene interrotto durante l'attesa
     */
    Item take() throws InterruptedException;

    /**
     * Numero massimo di item che il buffer può contenere. Non cambia mai.
     */
    int capacity();
}
