package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The addresses will be written to a file called {@value #SNAPSHOT_SPENTADDRESSES_FILE}.
 * The folder location is provided by {@link IotaConfig#getLocalSnapshotsBasePath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProviderImpl.class);

    private RocksDBPersistenceProvider rocksDBPersistenceProvider;

    private SnapshotConfig config;

    /**
     * Creates a new instance of SpentAddressesProvider
     */
    public SpentAddressesProviderImpl() {
        this.rocksDBPersistenceProvider = new RocksDBPersistenceProvider("spent-addresses-db",
                "spent-addresses-log", 1000,
                new HashMap<String, Class<? extends Persistable>>(1)
                {{put("spent-addresses", SpentAddress.class);}}, null);
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     *
     * @param config The snapshot configuration used for file location
     * @return the current instance
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public SpentAddressesProviderImpl init(SnapshotConfig config)
            throws SpentAddressesException {
        this.config = config;
        try {
            this.rocksDBPersistenceProvider.init();
            readPreviousEpochsSpentAddresses();
        }
        catch (Exception e) {
            throw new SpentAddressesException("There is a problem with accessing stored spent addresses", e);
        }
        return this;
    }

    private void readPreviousEpochsSpentAddresses() {
        if (config.isTestnet()) {
            return;
        }

        for (String previousEpochsSpentAddressesFile : config.getPreviousEpochSpentAddressesFiles().split(" ")) {
            try {
                readSpentAddressesFromStream(
                        SpentAddressesProviderImpl.class.getResourceAsStream(previousEpochsSpentAddressesFile));
            } catch (SpentAddressesException e) {
                log.error("Failed to read spent addresses from " + previousEpochsSpentAddressesFile, e);
            }
        }
    }

    private void readSpentAddressesFromStream(InputStream in) throws SpentAddressesException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                saveAddress(HashFactory.ADDRESS.create(line));
            }
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public boolean containsAddress(Hash addressHash) throws SpentAddressesException {
        try {
            return ((SpentAddress) rocksDBPersistenceProvider.get(SpentAddress.class, addressHash)).exists;
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddress(Hash addressHash) throws SpentAddressesException {
        try {
            rocksDBPersistenceProvider.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddressesBatch(Collection<Hash> addressHash) throws SpentAddressesException {
        try {
            // Its bytes are always new byte[0], therefore identical in storage
            SpentAddress spentAddressModel = new SpentAddress();

            rocksDBPersistenceProvider.saveBatch(addressHash
                .stream()
                .map(address -> new Pair<Indexable, Persistable>(address, spentAddressModel))
                .collect(Collectors.toList())
            );
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }
}
