package producerconsumer.event;

import producerconsumer.model.Item;

/**
 * Osservatore delle operazioni sul buffer eseguite da producer e consumer.
 *
 * Gli eventi before sono notificati prima della chiamata al buffer, gli eventi on dopo
 * un'operazione riuscita. Un'operazione interrotta ha solo l'evento before. La poison pill
 * non genera eventi: il suo inserimento non è notificato e il suo prelievo ha solo
 * {@link #beforeTake(String)}.
 *
 * I metodi sono chiamati dai thread della simulazione, anche in contemporanea: le
 * implementazioni devono essere thread-safe e brevi, perché il tempo speso in un metodo
 * ritarda il thread che lo chiama.
 */
public interface SimulationListener {

    /** Listener che ignora tutti gli eventi, per le esecuzioni senza osservatori. */
    SimulationListener NONE = new SimulationListener() {
        @Override
        public void beforePut(String thread) {
        }

        @Override
        public void onPut(String thread, Item item) {
        }

        @Override
        public void beforeTake(String thread) {
        }

        @Override
        public void onTake(String thread, Item item) {
        }
    };

    /** @param thread nome del thread che sta per chiamare put */
    void beforePut(String thread);

    /**
     * @param thread nome del thread che ha eseguito il put
     * @param item   item accettato dal buffer
     */
    void onPut(String thread, Item item);

    /** @param thread nome del thread che sta per chiamare take */
    void beforeTake(String thread);

    /**
     * @param thread nome del thread che ha eseguito il take
     * @param item   item prelevato dal buffer
     */
    void onTake(String thread, Item item);
}
