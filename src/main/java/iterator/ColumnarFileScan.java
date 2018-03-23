package iterator;

import columnar.ByteToTuple;
import columnar.ColumnarFile;
import columnar.TupleScan;
import global.AttrType;
import global.RID;
import global.TID;
import heap.InvalidTupleSizeException;
import heap.Tuple;

import java.io.IOException;
import java.util.ArrayList;

public class ColumnarFileScan extends Iterator {
    private ColumnarFile columnarFile;
    private CondExpr[] condExprs;
    private FldSpec[] projList;
    private AttrType[] attrTypes;
    private short[] stringSizes;
    private int attrLength;
    private int nOutFields;
    private Tuple tuple;
    private TupleScan tupleScan;
    private int tupleSize;
    private int[] columnNosArray;
    ByteToTuple byteToTuple;
    CondExprEval condExprEval;

    public ColumnarFileScan(String fileName, AttrType[] attrTypes
            , short stringSizes[], int attrLength, int nOutFields
            , FldSpec[] projList, CondExpr[] condExprs) throws ColumnarFileScanException {
        this.attrTypes = attrTypes;
        this.projList = projList;
        this.columnNosArray = new int[attrTypes.length];
        this.condExprs = condExprs;
        this.stringSizes = stringSizes;
        this.attrLength = attrLength;
        this.nOutFields = nOutFields;
        this.tuple = new Tuple();
        this.byteToTuple = new ByteToTuple(attrTypes);
        this.condExprEval = new CondExprEval(attrTypes, condExprs);
        for (int i = 0; i < attrTypes.length; i++)
            columnNosArray[i] = attrTypes[i].getColumnId();
        try {
            //tuple.setHdr((short) this.attrLength, this.attrTypes, this.stringSizes);
        } catch (Exception e) {
            throw new ColumnarFileScanException(e, "Tuple set hdr error");
        }
        this.tupleSize = tuple.getLength();
        try {
            columnarFile = new ColumnarFile(fileName);
        } catch (Exception e) {
            throw new ColumnarFileScanException(e, "Not able to create columnar file");
        }
        try {
            tupleScan = new TupleScan(columnarFile, columnNosArray);
        } catch (Exception e) {
            throw new ColumnarFileScanException(e, "Not able to initiate tuplescan");
        }
    }

    public FldSpec[] getProjList() {
        return projList;
    }


    public Tuple getNext() throws Exception {
        Tuple projectedTuple = null;
        RID rids[] = new RID[attrTypes.length];
        for (int i = 0; i < attrTypes.length; i++) rids[i] = new RID();
        TID tid = new TID(attrTypes.length, 0, rids);

        tuple = tupleScan.getNext(tid);

        while (tuple != null) {
            ArrayList<byte[]> arrayList = byteToTuple.setTupleBytes(tuple.getTupleByteArray());
            Tuple  projectTuples[] = new Tuple[projList.length] ;
            int sizeOfProjectTuple = 0;
            if (condExprEval.isValid(arrayList)) {
            	for(int i =0; i<projList.length;i++) {
            		projectTuples[i] = new Tuple(arrayList.get(i),0,arrayList.get(i).length);
            		sizeOfProjectTuple += arrayList.get(i).length;
            	}

            	projectedTuple = byteToTuple.mergeTuples(projectTuples, sizeOfProjectTuple);
                break;
            }
            tuple = tupleScan.getNext(tid);
        }
        return projectedTuple;
    }

    @Override
    public void close() throws IOException {
        tupleScan.closeTupleScan();
    }

    public Integer[] getPositions() throws IOException, InvalidTupleSizeException {
        Tuple projectedTuple = null;
        RID rids[] = new RID[attrTypes.length];
        for (int i = 0; i < attrTypes.length; i++) rids[i] = new RID();
        TID tid = new TID(attrTypes.length, 0, rids);
        ArrayList<Integer> positions = new ArrayList<Integer>();

        tuple = tupleScan.getNext(tid);
        int currentPosition = 0;
        while (tuple != null) {
            ArrayList<byte[]> arrayList = byteToTuple.setTupleBytes(tuple.getTupleByteArray());
            if (condExprEval.isValid(arrayList)) {
                positions.add(currentPosition);
            }

            tuple = tupleScan.getNext(tid);
        }

        return (Integer[]) positions.toArray();
    }
}
