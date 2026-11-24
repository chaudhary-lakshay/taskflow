// Jest only. react-router's ESM build reads import.meta.hot (a Vite dev feature). CommonJS has no
// import.meta, so Babel cannot compile it and Jest refuses the file. Replace it with an empty object:
// import.meta.hot is then undefined, which is what it is in any non-Vite environment.
module.exports = function importMetaStub({ types: t }) {
  return {
    name: 'import-meta-stub',
    visitor: {
      MetaProperty(path) {
        if (path.node.meta.name === 'import' && path.node.property.name === 'meta') {
          path.replaceWith(t.objectExpression([]))
        }
      },
    },
  }
}
