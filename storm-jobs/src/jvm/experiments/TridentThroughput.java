package experiments;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.Grouping;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storm.trident.Stream;
import storm.trident.TridentTopology;
import storm.trident.operation.Function;
import storm.trident.operation.TridentCollector;
import storm.trident.operation.TridentOperationContext;
import storm.trident.spout.IBatchSpout;
import storm.trident.tuple.TridentTuple;

import java.util.Map;

/**
 * Created by robert on 6/25/15.
 */
public class TridentThroughput {

	public static Logger LOG = LoggerFactory.getLogger(TridentThroughput.class);

	public static class Generator implements IBatchSpout {

		private final int delay;
		private final boolean withFt;
		private final int batchSize;
		private SpoutOutputCollector spoutOutputCollector;
		private int tid;
		private byte[] payload;
		private ParameterTool pt;
		public Generator(ParameterTool pt) {
			this.pt = pt;
			this.payload = new byte[pt.getInt("payload")];
			this.delay = pt.getInt("delay");
			this.withFt = pt.has("ft");
			this.batchSize = pt.getInt("batchSize");
		}

		@Override
		public void open(Map conf, TopologyContext context) {
			this.tid = context.getThisTaskId();
		}

		/**
		 * assumes that batchId starts at 0, and is increasing
		 *
		 * @param batchId
		 * @param collector
		 */
		@Override
		public void emitBatch(long batchId, TridentCollector collector) {
			long id = (batchId-1) * this.batchSize;
			for(int i = 0; i < this.batchSize; i++) {
				collector.emit(new Values(id, this.tid, this.payload));
				id++;
			}
		}

		@Override
		public void ack(long batchId) {
		}

		@Override
		public void close() {

		}

		@Override
		public Map getComponentConfiguration() {
			return null;
		}

		@Override
		public Fields getOutputFields() {
			return new Fields("id", "taskId", "payload");
		}
	}

	public static class Sink implements Function {
		long received = 0;
		long start = 0;
		ParameterTool pt;
		private long logfreq;

		public Sink(ParameterTool pt) {
			this.pt = pt;
			this.logfreq = pt.getInt("logfreq");
		}



		@Override
		public void prepare(Map conf, TridentOperationContext context) {

		}

		@Override
		public void cleanup() {

		}

		@Override
		public void execute(TridentTuple tuple, TridentCollector collector) {

			if(start == 0) {
				start = System.currentTimeMillis();
			}
			received++;
			if(received % logfreq == 0) {
				long sinceSec = ((System.currentTimeMillis() - start)/1000);
				if(sinceSec == 0) return;
				LOG.info("Received {} elements since {}. Elements per second {}, GB received {}",
						received,
						sinceSec,
						received / sinceSec,
						(received * (8 + 4 + pt.getInt("payload"))) / 1024 / 1024 / 1024);
			}
		}
	}



	public static void main(String[] args) throws Exception {
		ParameterTool pt = ParameterTool.fromArgs(args);

		int par = pt.getInt("para");


		TridentTopology topology = new TridentTopology();
		Stream sourceStream = topology.newStream("source", new Generator(pt));

		sourceStream.partitionBy(new Fields("id")).each(new Fields("id", "taskId", "payload"), new Sink(pt), new Fields("dontcare"));

		Config conf = new Config();
		conf.setDebug(false);

		if (!pt.has("local")) {
			conf.setNumWorkers(par);

			StormSubmitter.submitTopologyWithProgressBar("throughput", conf, topology.build());
		}
		else {
			conf.setMaxTaskParallelism(par);

			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology("throughput", conf, topology.build());

			Thread.sleep(30000);

			cluster.shutdown();
		}

	}
}
