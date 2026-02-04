package io.endee.client.util;

import io.endee.client.exception.EndeeException;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MessagePack serialization utilities.
 */
public final class MessagePackUtils {

    private MessagePackUtils() {
    }

    /**
     * Packs vector data for upsert operations.
     */
    public static byte[] packVectors(List<Object[]> vectors) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packArrayHeader(vectors.size());

            for (Object[] vector : vectors) {
                packVectorTuple(packer, vector);
            }

            return packer.toByteArray();
        } catch (IOException e) {
            throw new EndeeException("Failed to pack vectors", e);
        }
    }

    private static void packVectorTuple(MessageBufferPacker packer, Object[] vector) throws IOException {
        packer.packArrayHeader(vector.length);

        // id (string)
        packer.packString((String) vector[0]);

        // metadata (bytes)
        byte[] meta = (byte[]) vector[1];
        packer.packBinaryHeader(meta.length);
        packer.writePayload(meta);

        // filter (string)
        packer.packString((String) vector[2]);

        // norm (double)
        packer.packDouble((Double) vector[3]);

        // vector (double[])
        double[] vec = (double[]) vector[4];
        packer.packArrayHeader(vec.length);
        for (double v : vec) {
            packer.packDouble(v);
        }

        // Optional sparse data
        if (vector.length > 5) {
            int[] sparseIndices = (int[]) vector[5];
            packer.packArrayHeader(sparseIndices.length);
            for (int idx : sparseIndices) {
                packer.packInt(idx);
            }

            double[] sparseValues = (double[]) vector[6];
            packer.packArrayHeader(sparseValues.length);
            for (double val : sparseValues) {
                packer.packDouble(val);
            }
        }
    }

    /**
     * Unpacks query results from MessagePack bytes.
     */
    public static List<Object[]> unpackQueryResults(byte[] data) {
        List<Object[]> results = new ArrayList<>();

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            int arraySize = unpacker.unpackArrayHeader();

            for (int i = 0; i < arraySize; i++) {
                int tupleSize = unpacker.unpackArrayHeader();
                Object[] tuple = new Object[tupleSize];

                tuple[0] = unpackNumberAsDouble(unpacker); // similarity
                tuple[1] = unpacker.unpackString(); // vectorId
                int metaLen = unpacker.unpackBinaryHeader();
                tuple[2] = unpacker.readPayload(metaLen); // metadata
                tuple[3] = unpacker.unpackString(); // filter
                tuple[4] = unpackNumberAsDouble(unpacker); // norm

                if (tupleSize > 5) {
                    int vecLen = unpacker.unpackArrayHeader();
                    double[] vec = new double[vecLen];
                    for (int j = 0; j < vecLen; j++) {
                        vec[j] = unpacker.unpackDouble();
                    }
                    tuple[5] = vec;
                }

                results.add(tuple);
            }
        } catch (IOException e) {
            throw new EndeeException("Failed to unpack query results", e);
        }

        return results;
    }

    /**
     * Unpacks a single vector from MessagePack bytes.
     */
    public static Object[] unpackVector(byte[] data) {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            int tupleSize = unpacker.unpackArrayHeader();
            Object[] tuple = new Object[tupleSize];

            tuple[0] = unpacker.unpackString(); // id
            int metaLen = unpacker.unpackBinaryHeader();
            tuple[1] = unpacker.readPayload(metaLen); // metadata
            tuple[2] = unpacker.unpackString(); // filter
            tuple[3] = unpackNumberAsDouble(unpacker); // norm

            int vecLen = unpacker.unpackArrayHeader();
            double[] vec = new double[vecLen];
            for (int i = 0; i < vecLen; i++) {
                vec[i] = unpackNumberAsDouble(unpacker);
            }
            tuple[4] = vec;

            return tuple;
        } catch (IOException e) {
            throw new EndeeException("Failed to unpack vector", e);
        }
    }

    /**
     * Helper function to unpackNumber as double even though it
     * can be integer
     * @param unpacker
     * @return
     * @throws IOException
     */
    private static double unpackNumberAsDouble(MessageUnpacker unpacker) throws IOException {
        Value value = unpacker.unpackValue();

        if (value.isFloatValue()) {
            return value.asFloatValue().toDouble();
        }

        if (value.isIntegerValue()) {
            return value.asIntegerValue().toDouble();
        }

        throw new IllegalStateException(
                "Expected numeric value (int/float), got " + value.getValueType());
    }
}
