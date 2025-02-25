package research.diffsearch.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import research.diffsearch.Config;
import research.diffsearch.pipeline.OnlinePipeline;
import research.diffsearch.pipeline.RecallPipeline;
import research.diffsearch.server.PythonRunner;
import research.diffsearch.util.FilePathUtils;
import research.diffsearch.util.Util;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static research.diffsearch.pipeline.feature.FeatureExtractionPipeline.getDefaultFeatureExtractionPipeline;

public class ScalabilityMode extends App {
    private static final Logger logger = LoggerFactory.getLogger(NormalMode.class);

    private final Scanner scanner = new Scanner(System.in);

    @Override
    public void run() {
        StringBuilder sb = null;
        PrintWriter writer = null;
        int[] changes = {10000, 50000, 100000, 250000, 400000, 500000, 600000, 700000, 850000, 1000000};
        //int[] changes = {10000, 50000};
        int[] partitions = {4, 16, 28, 64, 100, -1, -1, -1, -1, -1};
        //int[] partitions = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        //int delay = 5;
        int pos = -1;

        try {
            OutputStream os = new FileOutputStream("./src/main/resources/Scalability/" + Config.PROGRAMMING_LANGUAGE + "/" + Config.PROGRAMMING_LANGUAGE + "_Results_scalability.csv");
            writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
            sb = new StringBuilder();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        for (int i : changes) {
            pos++;
            Config.nlist = (int) (Math.sqrt(i));
            Config.nprobe = (int) (32 * (Config.nlist / 1000.0));
            Config.k = i;
            Config.k_max = i;

            try {
                var pythonRunner = new PythonRunner(
                        "./src/main/resources/Python/FAISS_indexing_scalability.py",
                        FilePathUtils.getFeatureCSVPath(Config.PROGRAMMING_LANGUAGE),
                        FilePathUtils.getIndexFilePath(Config.PROGRAMMING_LANGUAGE),
                        Integer.toString(getDefaultFeatureExtractionPipeline(false).getTotalFeatureVectorLength()),
                        Integer.toString(Config.nlist),
                        Integer.toString(i),
                        Integer.toString(partitions[pos]),
                        Boolean.toString(Config.TFIDF));

                pythonRunner.runAndWaitUntilEnd();

                startPythonServer();

                Socket socketFaiss = getNewFaissSocket();

                // Open the file
                FileInputStream fstream = new FileInputStream("./src/main/resources/Scalability/"+Config.PROGRAMMING_LANGUAGE+"/scalability_queries.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

                String nextQuery;

                //Read File Line By Line
                while ((nextQuery = br.readLine()) != null) {
                    long time_sum = 0;

                    for (int j = 1; j <= 1; j++) {
                        long startTimeMatching = System.currentTimeMillis();
                        new OnlinePipeline(socketFaiss, Config.PROGRAMMING_LANGUAGE)
                                // add recall pipeline if necessary
                                .connectIf(Config.MEASURE_RECALL, new RecallPipeline(Config.PROGRAMMING_LANGUAGE, nextQuery))
                                .peek(result -> logger.info("Found {} results", result.getResults().size()))
                                .peek(Util::printOutputList)
                                .execute(nextQuery);
                        time_sum += (System.currentTimeMillis() - startTimeMatching);


                    }
                    logger.info("Final matching time: " + time_sum / 1000.0);
                    assert sb != null;
                    sb.append(Config.FAISS_scalability_time + ",");
                    sb.append(time_sum / 1000.0 + ",");
                }
                sb.append("\n");
                socketFaiss.close();
                //close();
                stopPythonServer();

            } catch (IOException | InterruptedException exception) {
                logger.error(exception.getMessage(), exception);
            }
        }
        writer.write(sb.toString());
        writer.close();

    }
}
