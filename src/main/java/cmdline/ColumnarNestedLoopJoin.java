package cmdline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import bitmap.AddFileEntryException;
import bitmap.BitMapScanException;
import bitmap.ConstructPageException;
import bitmap.GetFileEntryException;
import bitmap.PinPageException;
import bitmap.UnpinPageException;
import columnar.ColumnarFile;
import columnar.ColumnarFileDoesExistsException;
import columnar.ColumnarFilePinPageException;
import columnar.ColumnarFileUnpinPageException;
import diskmgr.DiskMgrException;
import global.AttrOperator;
import global.AttrType;
import global.IndexType;
import global.SystemDefs;
import heap.HFBufMgrException;
import heap.HFDiskMgrException;
import heap.HFException;
import heap.InvalidSlotNumberException;
import iterator.BitmapScan;
import iterator.BtreeScan;
import iterator.ColumnScan;
import iterator.ColumnarFileScan;
import iterator.ColumnarFileScanException;
import iterator.CondExpr;
import iterator.FldSpec;
import iterator.IndexException;
import iterator.Iterator;
import iterator.RelSpec;

public class ColumnarNestedLoopJoin {

    private static String columnDBName;
    private static String outerFile;
    private static String innerFile;
    private static ArrayList<String> targetColumnNames;
    private static StringBuffer outerConst = new StringBuffer();
    private static StringBuffer innerConst = new StringBuffer();
    private static StringBuffer joinConst = new StringBuffer();
    private static int numBuf;
    private static IndexType indexType;
    private static AttrType[] in1;
    private static AttrType[] in2;
    private static CondExpr[] outerCondExpr;
    private static CondExpr[] innerCondExpr;
    private static CondExpr joinCondExpr;

    // int c = 0;

    public static void main(String argv[]) throws Exception {
	initFromArgs(argv);
    }

    /*
     * Function to parse the arguments
     */
    private static void initFromArgs(String argv[]) throws Exception {
	int lengthOfArgv = argv.length;
	SystemDefs systemDefs = new SystemDefs(columnDBName, 0, numBuf, "LRU");
	targetColumnNames = new ArrayList<String>();
	columnDBName = argv[0];
	outerFile = argv[1];
	innerFile = argv[2];
	int i = 0;
	if (argv[3].equals("[")) {
	    i = 4;
	    while (!argv[i].equals("]")) {
		if (argv[i].equals("(") || argv[i].equals(")"))
		    i++;
		else {
		    outerConst.append(argv[i] + " ");
		    i++;
		}
	    }
	}
	if (argv[i + 1].equals("[")) {
	    i = i + 2;
	    while (!argv[i].equals("]")) {
		if (argv[i].equals("(") || argv[i].equals(")"))
		    i++;
		else {
		    innerConst.append(argv[i] + " ");
		    i++;
		}
	    }
	}
	if (argv[i + 1].equals("[")) {
	    i = i + 2;
	    while (!argv[i].equals("]")) {
		joinConst.append(argv[i] + " ");
		i++;
	    }
	}
	indexType = getIndexType(argv[i + 1]);
	i = i + 1;
	for (i = i + 2; i <= lengthOfArgv - 3; i++) {
	    targetColumnNames.add(argv[i]);
	}
	outerConst.deleteCharAt(outerConst.length() - 1);
	innerConst.deleteCharAt(innerConst.length() - 1);
	joinConst.deleteCharAt(joinConst.length() - 1);
	numBuf = Integer.parseInt(argv[lengthOfArgv - 1]);
	setUpJoin();
    }

    private static void setUpJoin() throws ColumnarFileScanException,
	    BitMapScanException, IndexException, Exception {
	ColumnarFile columnarFile = new ColumnarFile(outerFile);

	ArrayList<FldSpec> proj1 = new ArrayList<FldSpec>();
	ArrayList<FldSpec> proj2 = new ArrayList<FldSpec>();

	in1 = columnarFile.getColumnarHeader().getColumns();

	columnarFile = new ColumnarFile(innerFile);
	in2 = columnarFile.getColumnarHeader().getColumns();

	outerCondExpr = CommandLineHelper.parseToCondExpr(outerFile,
		outerConst);
	innerCondExpr = CommandLineHelper.parseToCondExpr(innerFile,
		innerConst);
	joinCondExpr = parseJoinConstr(outerFile, innerFile, joinConst);

	for (int i = 0; i < targetColumnNames.size(); i++) {
	    for (int j = 0; j < in1.length; j++) {
		if (targetColumnNames.get(i).equals(in1[j].getAttrName())) {

		    FldSpec fldSpec = new FldSpec(new RelSpec(0),
			    in1[j].getColumnId());
		    proj1.add(fldSpec);
		}
	    }
	}
	for (int i = 0; i < targetColumnNames.size(); i++) {
	    for (int j = 0; j < in1.length; j++) {
		if (targetColumnNames.get(i).equals(in2[j].getAttrName())) {

		    FldSpec fldSpec = new FldSpec(new RelSpec(0),
			    in1[j].getColumnId());
		    proj2.add(fldSpec);
		}
	    }

	}
	FldSpec projList1[] = proj1.toArray(new FldSpec[proj1.size()]);
	FldSpec projList2[] = proj1.toArray(new FldSpec[proj1.size()]);
	Iterator am1 = getIterator(outerFile, in1, null, in1.length,
		projList1.length, projList1, outerCondExpr);
	// Call to NLJ
	// new NestedLoopJoins(in1, in1.length, null, in2, in2.length, null,
	// numBuf, am1, innerFile, outerCondExpr, innerCondExpr, projList1,
	// projList2, projList1.length+ projList2.length);
    }

    public static CondExpr parseJoinConstr(String fileName1, String fileName2,
	    StringBuffer joinConst)
	    throws DiskMgrException, ColumnarFileDoesExistsException,
	    ColumnarFilePinPageException, HFException, HFBufMgrException,
	    HFDiskMgrException, ColumnarFileUnpinPageException,
	    bitmap.PinPageException, AddFileEntryException, UnpinPageException,
	    bitmap.ConstructPageException, bitmap.GetFileEntryException,
	    IOException, InvalidSlotNumberException {

	int index1 = -1, index2 = -1;
	ColumnarFile columnarFile1 = new ColumnarFile(fileName1);
	ColumnarFile columnarFile2 = new ColumnarFile(fileName2);
	String joinConstr = joinConst.toString();
	AttrType attrTypes1[] = columnarFile1.getColumnarHeader().getColumns();
	AttrType attrTypes2[] = columnarFile2.getColumnarHeader().getColumns();
	List<String> columnNames1 = new ArrayList<String>();
	List<String> columnNames2 = new ArrayList<String>();
	for (int i = 0; i < attrTypes1.length; i++) {
	    columnNames1.add(attrTypes1[i].getAttrName());
	}
	for (int i = 0; i < attrTypes2.length; i++) {
	    columnNames2.add(attrTypes2[i].getAttrName());
	}
	CondExpr condExpr = new CondExpr();
	condExpr.next = null;
	condExpr.op = CommandLineHelper.parseOperator(joinConstr);
	String op = CommandLineHelper.getOperatorSymbol(condExpr.op);
	String splitExpr[] = joinConstr.split(" " + op + " ");
	if (columnNames1.toString().contains(splitExpr[0])) {
	    index1 = columnNames1.indexOf(splitExpr[0]);

	}
	if (columnNames2.toString().contains(splitExpr[1])) {
	    index2 = columnNames2.indexOf(splitExpr[1]);

	}
	condExpr.operand1.symbol = new FldSpec(new RelSpec(0),
		attrTypes1[index1].getColumnId());
	condExpr.operand2.symbol = new FldSpec(new RelSpec(0),
		attrTypes2[index2].getColumnId());
	condExpr.type1 = new AttrType(AttrType.attrSymbol);
	condExpr.type2 = new AttrType(AttrType.attrSymbol);

	return condExpr;
    }

    /*
     * Function to parse the Index type
     */
    private static IndexType getIndexType(String indexName) {
	if (indexName.equals("FILESCAN"))
	    return new IndexType(0);
	if (indexName.equals("COLUMNSCAN"))
	    return new IndexType(4);
	if (indexName.equals("BTREE"))
	    return new IndexType(1);
	if (indexName.equals("BITMAP"))
	    return new IndexType(3);
	else
	    return null;
    }

    private static Iterator getIterator(String columnarFileName, AttrType[] in,
	    short[] strSizes, int counterIn, int counterFld, FldSpec[] projList,
	    CondExpr[] condition) throws ColumnarFileScanException,
	    HFBufMgrException, InvalidSlotNumberException,
	    GetFileEntryException, PinPageException, ConstructPageException,
	    IOException, IndexException, BitMapScanException, Exception {
	Iterator iter = null;
	if (indexType.indexType == IndexType.None) {
	    iter = new ColumnarFileScan(columnarFileName, in, strSizes,
		    counterIn, counterFld, projList, condition);
	}
	if (indexType.indexType == IndexType.ColumnScan) {
	    iter = new ColumnScan(columnarFileName, in, strSizes, counterIn,
		    counterFld, projList, condition);
	}
	if (indexType.indexType == IndexType.B_Index) {
	    boolean indexOnly = false;
	    if (counterIn == 2
		    && in[0].getAttrName().equals(in[1].getAttrName()))
		indexOnly = true;
	    iter = new BtreeScan(columnarFileName, in, strSizes, counterIn,
		    counterFld, projList, condition, indexOnly);
	}
	if (indexType.indexType == IndexType.BitMapIndex) {
	    iter = new BitmapScan(columnarFileName, in, strSizes, counterIn,
		    counterFld, projList, condition);
	}
	return iter;
    }

}