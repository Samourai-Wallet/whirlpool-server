package com.samourai.whirlpool.server.services;

import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.io.File;
import java.io.FileWriter;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportHandler<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CSVWriter writer;
  private StatefulBeanToCsv<T> csv;

  public ExportHandler(
      WhirlpoolServerConfig.ExportItemConfig exportItemConfig,
      Class<T> exportType,
      String[] headers)
      throws Exception {
    // verify directory exists
    String dirname = exportItemConfig.getDirectory();
    File exportDirectory = new File(dirname);
    if (!exportDirectory.isDirectory()) {
      throw new Exception("export directory doesn't exist: " + dirname);
    }

    // create file if not exists
    boolean justCreated = false;
    String filename = exportItemConfig.getFilename();
    File csvFile = new File(exportDirectory, filename);
    if (!csvFile.exists()) {
      csvFile.createNewFile();
      if (!csvFile.exists()) {
        throw new Exception("export file doesn't exist: " + filename + " in " + dirname);
      }
      justCreated = true;
    }

    // verify file is writable
    if (!csvFile.canWrite()) {
      throw new Exception("export file is not writable: " + filename + " in " + dirname);
    }

    log.info("Ready to export: " + exportType.getName() + " => " + csvFile.getAbsolutePath());

    // map type to CSV
    this.writer = new CSVWriter(new FileWriter(csvFile, true));

    ColumnPositionMappingStrategy mapStrategy = new ColumnPositionMappingStrategy();
    mapStrategy.setType(exportType);

    this.csv =
        new StatefulBeanToCsvBuilder<>(writer)
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
  }

  public void write(T bean) throws Exception {
    csv.write(bean);
    writer.flush();
  }
}
