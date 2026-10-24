'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { VariantDTO } from '@/types/api';

interface VariantSelectorProps {
  variantOptions: Record<string, string[]>;
  variants: VariantDTO[];
  selectedAttributes: Record<string, string>;
  onSelectAttribute: (attrName: string, val: string) => void;
}

export function VariantSelector({
  variantOptions,
  variants,
  selectedAttributes,
  onSelectAttribute,
}: VariantSelectorProps) {
  const tAttr = useTranslations('attributes');

  if (!variantOptions || Object.keys(variantOptions).length === 0) {
    return null;
  }

  const getAttrLabel = (attr: string) => {
    const key = attr.toLowerCase();
    if (['ram', 'color', 'storage', 'size', 'weight'].includes(key)) {
      return tAttr(key as any);
    }
    return attr.toUpperCase();
  };

  const attributeKeys = Object.keys(variantOptions);

  // Check if an option is possible based on prior selected attributes
  const isOptionPossible = (attrName: string, val: string) => {
    const attrIndex = attributeKeys.indexOf(attrName);
    if (attrIndex === 0) {
      // First attribute (e.g. RAM) is always selectable if any variant has it
      return variants.some((v) => v.attributes[attrName] === val);
    }

    // Dependent attributes check compatibility with the primary/prior selected attributes
    const priorKeys = attributeKeys.slice(0, attrIndex);
    return variants.some((v) => {
      if (v.attributes[attrName] !== val) return false;
      return priorKeys.every((priorKey) => {
        const priorVal = selectedAttributes[priorKey];
        return !priorVal || v.attributes[priorKey] === priorVal;
      });
    });
  };

  return (
    <div className="space-y-4" data-testid="variant-selector">
      {Object.entries(variantOptions).map(([attrName, options]) => (
        <div key={attrName} className="space-y-2">
          <div className="flex items-center justify-between text-xs font-semibold">
            <span className="uppercase tracking-wider text-muted">{getAttrLabel(attrName)}</span>
            <span className="text-foreground font-bold">
              {selectedAttributes[attrName] || ''}
            </span>
          </div>

          <div className="flex flex-wrap gap-2">
            {options.map((val) => {
              const isSelected = selectedAttributes[attrName] === val;
              const isPossible = isOptionPossible(attrName, val);

              return (
                <button
                  key={val}
                  type="button"
                  data-testid={`variant-chip-${attrName}-${val}`}
                  disabled={!isPossible}
                  onClick={() => onSelectAttribute(attrName, val)}
                  className={`px-3.5 py-1.5 rounded-btn text-xs font-medium border transition-all ${
                    isSelected
                      ? 'border-primary bg-primary text-primary-foreground font-bold shadow-xs'
                      : isPossible
                      ? 'border-border bg-surface text-foreground hover:border-primary/50'
                      : 'border-dashed border-border/50 bg-muted/10 text-muted/50 cursor-not-allowed line-through'
                  }`}
                >
                  {val}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
