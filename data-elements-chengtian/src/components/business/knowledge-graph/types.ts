export interface GraphField {
  id: string;
  fieldNameEn: string;
  fieldNameCn: string;
  fieldType: "VARCHAR" | "INT" | "DATETIME" | "DECIMAL";
  fieldLength: number;
  isPrimaryKey: boolean;
  isRelationField: boolean;
}

export interface GraphNode {
  id: string;
  nameEn: string;
  nameCn: string;
  color: string;
  icon: string;
  dataSource?: string;
  remark?: string;
  fields: GraphField[];
  x?: number;
  y?: number;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relNameEn: string;
  relNameCn: string;
}

export interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
}
