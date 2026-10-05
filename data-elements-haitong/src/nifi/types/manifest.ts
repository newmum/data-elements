// Mirrors backend records in com.example.nificanvas.manifest.*
// IMPORTANT: keep field names and optionality strictly aligned with the Java records.

export type ComponentCategory = 'source' | 'transform' | 'sink' | 'branch';

export type FieldType =
  | 'text'
  | 'password'
  | 'number'
  | 'select'
  | 'switch'
  | 'sql-editor'
  | 'json-editor'
  | 'field-mapping-dsl'
  | 'table-picker'
  | 'columns-mapper'
  | 'duration';

export interface FieldOption {
  label: string;
  value: string;
}

export interface VisibleWhen {
  key: string;
  equals: unknown;
}

export interface FieldSchema {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  default?: unknown;
  options?: FieldOption[];
  placeholder?: string;
  visibleWhen?: VisibleWhen;
  help?: string;
  group?: string;
}

export interface ControllerServiceSpec {
  localId: string;
  type: string;
  properties: Record<string, string>;
}

export interface ProcessorSpec {
  localId: string;
  type: string;
  properties: Record<string, string>;
  schedulingPeriod?: string;
  schedulingStrategy?: string;
}

export interface InternalConnection {
  from: string;
  to: string;
  relationship: string;
}

export interface Outlet {
  name: string;
  processorRef: string;
  relationship: string;
}

export interface CompileSpec {
  controllerServices?: ControllerServiceSpec[];
  processors?: ProcessorSpec[];
  internalConnections?: InternalConnection[];
  inlet?: string;
  outlets?: Outlet[];
}

export interface ComponentManifest {
  key: string;
  category: ComponentCategory;
  label: string;
  icon?: string;
  description?: string;
  fields?: FieldSchema[];
  compile?: CompileSpec;
}
