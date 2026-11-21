import type {ModelDocument} from './modelCatalogStore';

export function modelCatalogCategories(docs:readonly ModelDocument[]){
 return [...new Set(docs.map(doc=>doc.category))].sort();
}

export function filterModelCatalog(docs:readonly ModelDocument[],category?:string,search=''){
 const query=search.trim().toLowerCase();
 return docs.filter(doc=>(!category||doc.category===category)&&
  (doc.name+' '+doc.category).toLowerCase().includes(query));
}
