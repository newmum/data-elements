import type {ReactNode} from 'react';
import {
 DatabaseOutlined,FilterOutlined,TableOutlined,MergeCellsOutlined,FunctionOutlined,EyeOutlined,
 SortAscendingOutlined,VerticalAlignTopOutlined,EditOutlined,AppstoreAddOutlined,SwapOutlined,
 PlusSquareOutlined,CalculatorOutlined,HighlightOutlined,RetweetOutlined,ScissorOutlined,
 SplitCellsOutlined,AimOutlined,BlockOutlined,DiffOutlined,SearchOutlined,OrderedListOutlined,
 FieldNumberOutlined,LinkOutlined,SafetyCertificateOutlined,CheckCircleOutlined,BorderInnerOutlined,
 RiseOutlined,RotateRightOutlined,CalendarOutlined,NodeIndexOutlined,FontSizeOutlined,AuditOutlined,
 StepBackwardOutlined,PercentageOutlined,InsertRowRightOutlined,UnorderedListOutlined,ApartmentOutlined,
 BranchesOutlined,ScanOutlined,KeyOutlined,ControlOutlined,FieldStringOutlined,NumberOutlined,
 FileSearchOutlined,ApiOutlined,ClusterOutlined,SyncOutlined,CloudDownloadOutlined,FolderOpenOutlined,
 ProfileOutlined,SwitcherOutlined,LayoutOutlined,ClockCircleOutlined,FieldTimeOutlined,ShareAltOutlined,
 ForkOutlined,DashboardOutlined,HddOutlined,CodeOutlined,ConsoleSqlOutlined,NodeCollapseOutlined,
 SaveOutlined,SendOutlined,DeploymentUnitOutlined,CloudUploadOutlined,
} from '@ant-design/icons';
import type {NodeKind} from './modeling';

type FileFormat='csv'|'excel'|'json'|'parquet'|'xml';
const formatPaths:Record<FileFormat,string>={
 csv:'M7 8h10M7 11h10M7 14h10M10 7v8M14 7v8',
 excel:'M7 8l5 7M12 8l-5 7M15 8h3M15 11.5h3M15 15h3M16.5 8v7',
 json:'M10 7H9v3l-2 1 2 1v3h1M14 7h1v3l2 1-2 1v3h-1M12 9v.1M12 13v.1',
 parquet:'M7 9h2v6H7zM11 7h2v8h-2zM15 11h2v4h-2z',
 xml:'M10 8l-3 3 3 3M14 8l3 3-3 3M13 7l-2 8',
};
/** File shape, format glyph and arrow direction each carry meaning, independent of color. */
function FileGlyph({format,output=false}:{format:FileFormat;output?:boolean}){
 return <svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
  <path d="M5 16V3h10l4 4v9M15 3v4h4"/>
  <path d={formatPaths[format]}/>
  <path d={output?'M13 20h9m-3-3 3 3-3 3M5 19v1h5':'M2 20h9m-3-3 3 3-3 3M14 20h5v-1'}/>
 </svg>;
}
function DistinctGlyph(){
 return <svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
  <path d="M3 4h10v4H3zM3 11h10v4H3zM6 19h7M16 6l3 3-3 3M19 9h3M16 18l2 2 4-5"/>
 </svg>;
}
const icon=(id:string,glyph:ReactNode)=>({id,glyph});
/** Exhaustive semantic mapping: no kind falls back to a shared category icon. */
export const MODEL_OPERATOR_ICONS:Record<NodeKind,{id:string;glyph:ReactNode}>={
 source:icon('database-table-input',<DatabaseOutlined/>),
 filter:icon('row-filter',<FilterOutlined/>),
 select:icon('field-projection',<TableOutlined/>),
 join:icon('inner-table-join',<MergeCellsOutlined/>),
 aggregate:icon('group-aggregate',<FunctionOutlined/>),
 output:icon('result-preview',<EyeOutlined/>),
 sort:icon('ordered-rows',<SortAscendingOutlined/>),
 distinct:icon('deduplicate-rows',<DistinctGlyph/>),
 limit:icon('top-row-limit',<VerticalAlignTopOutlined/>),
 rename:icon('rename-field',<EditOutlined/>),
 union:icon('append-datasets',<AppstoreAddOutlined/>),
 cast:icon('convert-value-type',<SwapOutlined/>),
 constant:icon('constant-field',<PlusSquareOutlined/>),
 derived:icon('arithmetic-expression',<CalculatorOutlined/>),
 fillNull:icon('fill-missing-values',<HighlightOutlined/>),
 replace:icon('replace-value',<RetweetOutlined/>),
 trim:icon('trim-whitespace',<ScissorOutlined/>),
 split:icon('split-field',<SplitCellsOutlined/>),
 sample:icon('sample-rows',<AimOutlined/>),
 intersection:icon('set-intersection',<BlockOutlined/>),
 difference:icon('set-difference',<DiffOutlined/>),
 lookup:icon('lookup-by-key',<SearchOutlined/>),
 rank:icon('dense-rank',<OrderedListOutlined/>),
 sequence:icon('row-sequence',<FieldNumberOutlined/>),
 concat:icon('concatenate-fields',<LinkOutlined/>),
 mask:icon('mask-sensitive-values',<SafetyCertificateOutlined/>),
 exists:icon('key-existence',<CheckCircleOutlined/>),
 crossJoin:icon('cartesian-product',<BorderInnerOutlined/>),
 runningSum:icon('cumulative-sum',<RiseOutlined/>),
 unpivot:icon('columns-to-rows',<RotateRightOutlined/>),
 datePart:icon('date-part',<CalendarOutlined/>),
 jsonScalar:icon('json-property-path',<NodeIndexOutlined/>),
 stringify:icon('value-to-text',<FontSizeOutlined/>),
 assert:icon('quality-assertion',<AuditOutlined/>),
 lagLead:icon('previous-next-row',<StepBackwardOutlined/>),
 percentile:icon('statistical-percentile',<PercentageOutlined/>),
 pivot:icon('rows-to-columns',<InsertRowRightOutlined/>),
 rowSplit:icon('split-into-rows',<UnorderedListOutlined/>),
 flatten:icon('flatten-hierarchy',<ApartmentOutlined/>),
 conditional:icon('conditional-branch',<BranchesOutlined/>),
 fuzzy:icon('fuzzy-lookup',<ScanOutlined/>),
 surrogateKey:icon('generate-business-key',<KeyOutlined/>),
 alterRow:icon('mark-row-action',<ControlOutlined/>),
 regex:icon('text-pattern-check',<FieldStringOutlined/>),
 hash:icon('sha256-fingerprint',<NumberOutlined/>),
 xmlScalar:icon('xml-field-extraction',<FileSearchOutlined/>),
 csvInput:icon('csv-file-input',<FileGlyph format="csv"/>),
 jsonInput:icon('json-file-input',<FileGlyph format="json"/>),
 xmlInput:icon('xml-file-input',<FileGlyph format="xml"/>),
 excelInput:icon('excel-file-input',<FileGlyph format="excel"/>),
 parquetInput:icon('parquet-file-input',<FileGlyph format="parquet"/>),
 csvOutput:icon('csv-file-output',<FileGlyph format="csv" output/>),
 jsonOutput:icon('json-file-output',<FileGlyph format="json" output/>),
 xmlOutput:icon('xml-file-output',<FileGlyph format="xml" output/>),
 excelOutput:icon('excel-file-output',<FileGlyph format="excel" output/>),
 parquetOutput:icon('parquet-file-output',<FileGlyph format="parquet" output/>),
 httpInput:icon('http-api-input',<ApiOutlined/>),
 kafkaInput:icon('kafka-topic-input',<ClusterOutlined/>),
 cdcInput:icon('change-data-capture',<SyncOutlined/>),
 objectInput:icon('object-storage-input',<CloudDownloadOutlined/>),
 fileWatcher:icon('watch-directory',<FolderOpenOutlined/>),
 dimensionLookup:icon('dimension-table-lookup',<ProfileOutlined/>),
 slidingWindow:icon('overlapping-time-windows',<SwitcherOutlined/>),
 tumblingWindow:icon('adjacent-time-windows',<LayoutOutlined/>),
 sessionWindow:icon('session-time-gap',<ClockCircleOutlined/>),
 eventTimeWindow:icon('event-timestamp-window',<FieldTimeOutlined/>),
 broadcast:icon('broadcast-to-branches',<ShareAltOutlined/>),
 errorRoute:icon('route-errors',<ForkOutlined/>),
 rateLimit:icon('throughput-limit',<DashboardOutlined/>),
 cache:icon('cache-values',<HddOutlined/>),
 script:icon('script-configuration',<CodeOutlined/>),
 sql:icon('sql-configuration',<ConsoleSqlOutlined/>),
 mergeFlow:icon('merge-control-flows',<NodeCollapseOutlined/>),
 databaseOutput:icon('database-write-output',<SaveOutlined/>),
 httpOutput:icon('http-request-output',<SendOutlined/>),
 kafkaOutput:icon('kafka-topic-output',<DeploymentUnitOutlined/>),
 objectOutput:icon('object-storage-output',<CloudUploadOutlined/>),
};

export default function ModelOperatorIcon({kind}:{kind:NodeKind}){
 const {id,glyph}=MODEL_OPERATOR_ICONS[kind];
 return <span className="modeling-semantic-icon" data-model-icon={id} aria-hidden="true">{glyph}</span>;
}
