import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) all outputs claimed by {@code tx} are in the current UTXO pool
            if (!utxoPool.contains(utxo)) {
                return false;
            }

            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);

            // (2) the signatures on each input of {@code tx} are valid
            if (!Crypto.verifySignature(prevOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            // (3) no UTXO is claimed multiple times by {@code tx}
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            // (4) all of {@code tx}s output values are non-negative
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }

        // (5) the sum of tx's input values is greater than or equal to the sum of its output values
        if (inputSum < outputSum) {
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<>();

        while (true) {
            int acceptedCountThisRound = 0;
            for (Transaction tx : possibleTxs) {
                if (tx == null || acceptedTxs.contains(tx)) {
                    continue;
                }

                if (isValidTx(tx)) {
                    acceptedTxs.add(tx);
                    acceptedCountThisRound++;

                    // Remove spent UTXOs from the pool
                    for (Transaction.Input input : tx.getInputs()) {
                        UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                        utxoPool.removeUTXO(utxo);
                    }
                    
                    // Add new UTXOs to the pool
                    for (int i = 0; i < tx.numOutputs(); i++) {
                        Transaction.Output output = tx.getOutput(i);
                        UTXO utxo = new UTXO(tx.getHash(), i);
                        utxoPool.addUTXO(utxo, output);
                    }
                }
            }
            
            // If we go through a whole loop without accepting any new transactions, we're done.
            if (acceptedCountThisRound == 0) {
                break;
            }
        }

        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }
}