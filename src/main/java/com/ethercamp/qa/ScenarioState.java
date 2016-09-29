package com.ethercamp.qa;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.*;

/**
 * Created by Anton Nashatyrev on 20.05.2016.
 */
public class ScenarioState {


    public static class Median {
        private final double[] nums;
        private int idx;
        private boolean filled = false;

        public Median(int size) {
            nums = new double[size];
            idx = nums.length - 1;
        }

        public synchronized void add(double n) {
            if (idx == 0) {
                idx = nums.length - 1;
                filled = true;
            }
            nums[idx--] = n;
        }

        public boolean isFilled() {
            return filled;
        }

        public boolean isEmpty() {
            return !filled && idx == nums.length - 1;
        }

        public synchronized double getPercentile(int percent) {
            double[] sorted = Arrays.copyOf(nums, nums.length);
            Arrays.sort(sorted);
            double ret = sorted[nums.length * percent / 100];

            return ret;
        }

        public synchronized double[] getAllNums() {
            double[] ret = new double[nums.length];
            int cnt = 0;
            for (int i = idx - 1; i >= 0; i--) {
                ret[cnt++] = nums[i];
            }
            for (int i = nums.length - 1; i >= idx; i--) {
                ret[cnt++] = nums[i];
            }
            return ret;
        }
    }

    public class BlockInfo {
        public long timestamp;
        public int num;

        public BlockInfo(long timestamp, int num) {
            this.timestamp = timestamp;
            this.num = num;
        }
    }

    List<BlockInfo> blocks100K = new ArrayList<>();
    CircularFifoQueue<BlockInfo> lastBlocks = new CircularFifoQueue<>(1000);
    CircularFifoQueue<String> lastErrors = new CircularFifoQueue<>(20);

    enum State {
        Init,
        Ready,
        Running,
        Error,
        CompleteOK,
        CompleteFail
    }

    private State state = State.Init;
    private Throwable error;
    private String failReason;

    private int lastBlock;
    private long lastBlockTime;
    private Median blockIntervals = new Median(20);

    private int errorCount;

    private List<String> warnings = new ArrayList<>();

    Collection<ScenarioState> allStates = Collections.EMPTY_LIST;

    String lastCommand;
    Boolean lastResult;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public int getLastBlock() {
        return lastBlock;
    }

    public void newBlock(int lastBlock) {
        this.lastBlock = lastBlock;
        long curTime = System.currentTimeMillis();
        blockIntervals.add(curTime - lastBlockTime);
        this.lastBlockTime = curTime;
        BlockInfo blockInfo = new BlockInfo(curTime, lastBlock);
        lastBlocks.add(blockInfo);

        if (lastBlock % 100_000 == 0 || lastBlock == 1) {
            blocks100K.add(blockInfo);
        }

        if (getLastBlockDelay() > 3 * 60 * 1000) {
            addWarning("Large block delay: block:" + lastBlock + ", delay: " + getLastBlockDelay());
        }
        if (lastBlock > 0 && curTime - lastBlockTime > 3 * 60 * 1000) {
            addWarning("Long gab between block imports: lastBlock: " + lastBlock + ", delay: " + (curTime - lastBlockTime));
        }
    }

    boolean wasShort = false;
    public boolean wasShortSync() {
        return wasShort;
    }

    public boolean isShortSync() {
        boolean ret = blockIntervals.isFilled() && blockIntervals.getPercentile(50) > 2000;
        wasShort |= ret;
//        double v = blockIntervals.getPercentile(50);
        return ret;
    }

    public boolean hasAnyBlocks() {
        return !blockIntervals.isEmpty();
    }

    public long getLastBlockTime() {
        return lastBlockTime;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void resetErrors() {
        errorCount = 0;
        lastErrors.clear();
    }

    public Collection<String> getLastErrors() {
        return lastErrors;
    }

    public void addError(String error) {
        errorCount++;
        lastErrors.add(error);
    }

    public Median getBlockIntervals() {
        return blockIntervals;
    }

    public boolean isSameNet(ScenarioState state) {
        return isShortSync() && state.isShortSync() && Math.abs(lastBlock - state.lastBlock) < 256;
    }

    public long getLastBlockDelay() {
        long bestBlockNum = 0;
        long bestBlockTime = Long.MAX_VALUE;
        for (ScenarioState otherState : allStates) {
            if (isSameNet(otherState)) {
                if (otherState.getLastBlock() > bestBlockNum) {
                    bestBlockNum = otherState.lastBlock;
                    bestBlockTime = otherState.lastBlockTime;
                } else if (otherState.getLastBlock() == bestBlockNum) {
                    if (otherState.lastBlockTime < bestBlockTime) {
                        bestBlockTime = otherState.lastBlockTime;
                    }
                }
            }
        }
        return bestBlockNum == 0 ? 0 :
                (lastBlock == bestBlockNum ? lastBlockTime - bestBlockTime : System.currentTimeMillis() - bestBlockTime);
    }

    public List<BlockInfo> getBlocks100K() {
        return blocks100K;
    }

    public Long getBlockTimestamp(long blockNum) {
        for (int i = lastBlocks.size() - 1; i >= 0; i--) {
            if (lastBlocks.get(i).num < blockNum) return null;
            if (lastBlocks.get(i).num == blockNum) return lastBlocks.get(i).timestamp;
        }
        return null;
    }

    public void setAllStates(Collection<ScenarioState> allStates) {
        this.allStates = new ArrayList<>(allStates);
//        this.allStates.remove(this);
    }

    public void addWarning(String warn) {
        warnings.add(warn);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void resetWarnings() {
        warnings.clear();
    }

    public void commandStarted(String cmdName) {
        lastCommand = cmdName;
        lastResult = null;
    }

    public String getLastCommand() {
        return lastCommand;
    }

    public void setLastResult(boolean lastResult) {
        this.lastResult = lastResult;
    }

    public Boolean getLastResult() {
        return lastResult;
    }


    public static void main(String[] args) throws Exception {
        Median median = new Median(20);
        median.add(1698);
        median.add(6325);
        median.add(69000);
        median.add(10000);
        median.add(2077);
        median.add(2304);
        median.add(8613);
        median.add(3933);
        median.add(20000);
        median.add(4729);
        median.add(6992);
        median.add(14560);
        median.add(4984);
        median.add(8148);
        median.add(8235);
        median.add(19307);
        median.add(702);
        median.add(3604);
        median.add(21109);

        System.out.println(median.getPercentile(50));
    }
}
