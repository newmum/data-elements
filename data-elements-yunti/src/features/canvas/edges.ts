import { Shape } from "@antv/x6";

export const edgeAttrs = {
  line: {
    stroke: "#a5b2c6",
    strokeWidth: 1.5,
    targetMarker: {
      name: "block",
      size: 7,
    },
  },
};

export const selectedEdgeAttrs = {
  line: {
    stroke: "#1677ff",
    strokeWidth: 2.5,
    targetMarker: {
      name: "block",
      size: 8,
    },
  },
};

export const activeEdgeAttrs = {
  line: {
    stroke: "#1677ff",
    strokeWidth: 2,
    strokeDasharray: "8 4",
    targetMarker: {
      name: "block",
      size: 8,
    },
    style: {
      animation: "fs-edge-flow 0.8s linear infinite",
    },
  },
};

export const connectingOptions = {
  allowBlank: false,
  allowLoop: false,
  allowNode: false,
  allowEdge: false,
  highlight: true,
  snap: true,
  router: {
    name: "manhattan",
    args: {
      padding: 16,
    },
  },
  connector: {
    name: "rounded",
    args: {
      radius: 8,
    },
  },
  createEdge() {
    return new Shape.Edge({
      shape: "edge",
      attrs: edgeAttrs,
      zIndex: 0,
    });
  },
};
