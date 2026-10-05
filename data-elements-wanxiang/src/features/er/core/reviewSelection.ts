import type { Relationship } from '../types/domain';

/** Only current, healthy logical recommendations may enter a confirmation batch. */
export function canConfirmRelationship(relation: Relationship): boolean {
  return relation.origin !== 'catalog' && relation.reviewStatus === 'suggested' && relation.lifecycle !== 'broken';
}
