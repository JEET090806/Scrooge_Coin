import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a defensive copy of utxoPool by using the
     * UTXOPool(UTXOPool uPool) constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its
     * output values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> claimedUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) All outputs claimed by tx are in the current UTXO pool
            if (!utxoPool.contains(utxo)) {
                return false;
            }

            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);

            // (2) The signatures on each input of tx are valid
            if (!Crypto.verifySignature(prevOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            // (3) No UTXO is claimed multiple times by tx
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            // (4) All of tx's output values are non-negative
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }

        // (5) The sum of tx's input values is >= the sum of its output values
        if (inputSum < outputSum) {
            return false;
        }

        return true;
    }

    /**
     * Helper method to calculate the fee of a transaction.
     */
    private double calculateFee(Transaction tx) {
        double inputSum = 0;
        double outputSum = 0;

        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            // We assume the tx is valid, so the UTXO will be in the pool
            if (utxoPool.contains(utxo)) {
                 inputSum += utxoPool.getTxOutput(utxo).value;
            }
        }

        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }

        return inputSum - outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions that
     * maximizes the total transaction fees, and updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<>();
        Set<Transaction> remainingTxs = new HashSet<>();
        for (Transaction tx : possibleTxs) {
            remainingTxs.add(tx);
        }

        while (true) {
            Transaction bestTx = null;
            double maxFee = 0;

            // Find the valid transaction with the highest fee
            for (Transaction tx : remainingTxs) {
                if (isValidTx(tx)) {
                    double fee = calculateFee(tx);
                    if (fee > maxFee) {
                        maxFee = fee;
                        bestTx = tx;
                    }
                }
            }
            
            // If no valid transaction can be found, we are done
            if (bestTx == null) {
                break;
            }

            // Add the best transaction to our list and update the state
            acceptedTxs.add(bestTx);
            remainingTxs.remove(bestTx);

            // Update the UTXO pool: remove spent outputs
            for (Transaction.Input input : bestTx.getInputs()) {
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                utxoPool.removeUTXO(utxo);
            }

            // Update the UTXO pool: add new unspent outputs
            for (int i = 0; i < bestTx.numOutputs(); i++) {
                Transaction.Output output = bestTx.getOutput(i);
                UTXO utxo = new UTXO(bestTx.getHash(), i);
                utxoPool.addUTXO(utxo, output);
            }
        }
        
        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }
}