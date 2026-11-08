import { useEffect, type RefObject } from 'react';

/**
 * Entrée au défilement : chaque élément [data-reveal] du conteneur monte et apparaît une fois visible,
 * en cascade avec ses voisins. Sans script ou avec « réduire les animations », tout reste visible.
 */
export function useReveal(root: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const container = root.current;
    if (!container || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (!('IntersectionObserver' in window)) return;

    const items = [...container.querySelectorAll<HTMLElement>('[data-reveal]')];
    items.forEach((el) => {
      const siblings = el.parentElement ? [...el.parentElement.children].filter((c) => c.hasAttribute('data-reveal')) : [el];
      el.style.setProperty('--i', String(siblings.indexOf(el)));
      el.dataset.reveal = 'pending';
    });

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          (entry.target as HTMLElement).dataset.reveal = 'shown';
          observer.unobserve(entry.target);
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.05 },
    );
    items.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [root]);
}
