package com.samourai.whirlpool.server.services;

import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.export.MixCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.lang.invoke.MethodHandles;

@Service
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private WhirlpoolServerConfig serverConfig;

    private ExportHandler<MixCsv> exportMixs;

    public ExportService(WhirlpoolServerConfig serverConfig) throws Exception {
        this.serverConfig = serverConfig;
        init();
    }

    public void exportMix(Mix mix) {
        try {
            MixTO mixTO = mix.__getMixTO().get();
            MixCsv mixCSV = new MixCsv(mixTO);
            exportMixs.write(mixCSV);
        } catch(Exception e) {
            log.error("unable to export mix", e);
        }
    }

    private void init() throws Exception {
        // init export: mixs
        exportMixs = initExport(serverConfig.getExport().getMixs(), MixCsv.class, MixCsv.HEADERS);
    }

   private  <T> ExportHandler<T> initExport(WhirlpoolServerConfig.ExportItemConfig exportItemConfig, Class<T> exportType, String[] headers) throws Exception {
        // verify directory exists
        String dirname = exportItemConfig.getDirectory();
        File exportDirectory = new File(dirname);
        if (!exportDirectory.isDirectory()) {
            throw new Exception("export-mixs directory doesn't exist: " + dirname);
        }

        // create file if not exists
        boolean justCreated = false;
        String filename = exportItemConfig.getFilename();
        File csvFile = new File(exportDirectory, filename);
        if (!csvFile.exists()) {
            csvFile.createNewFile();
            if (!csvFile.exists()) {
                throw new Exception("export-mixs file doesn't exist: " + filename + " in " + dirname);
            }
            justCreated = true;
        }

        // verify file is writable
        if (!csvFile.canWrite()) {
            throw new Exception("export-mixs file is not writable: " + filename + " in " + dirname);
        }

        log.info("Ready to export: " + exportType.getName() + " => " + csvFile.getAbsolutePath());

        // map type to CSV
        CSVWriter writer = new CSVWriter(new FileWriter(csvFile, true));

        ColumnPositionMappingStrategy mapStrategy = new ColumnPositionMappingStrategy();
        mapStrategy.setType(exportType);

        StatefulBeanToCsv csv = new StatefulBeanToCsvBuilder<>(writer)
                .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                .withMappingStrategy(mapStrategy)
                .withSeparator(',')
                .withThrowExceptions(true)
                .build();

        if (justCreated) {
            // write headers
            writer.writeNext(headers);
            writer.flush();
        }

        return new ExportHandler<>(writer, csv);
    }

    private static class ExportHandler<T> {
        private CSVWriter writer;
        private StatefulBeanToCsv<T> csv;

        public ExportHandler(CSVWriter writer, StatefulBeanToCsv<T> csv) {
            this.writer = writer;
            this.csv = csv;
        }

        public void write(T bean) throws Exception {
            csv.write(bean);
            writer.flush();
        }
    }
}
