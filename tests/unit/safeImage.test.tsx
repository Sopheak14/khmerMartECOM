import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

vi.mock('next/image', () => ({
  default: ({ fill: _fill, priority: _priority, ...props }: any) => <img {...props} />,
}));

import { SafeImage } from '@/components/common/SafeImage';

describe('SafeImage', () => {
  it('renders the image while it loads', () => {
    render(<SafeImage src="https://placehold.co/800x800/png" alt="Silk" width={10} height={10} />);
    expect(screen.getByAltText('Silk')).toBeInTheDocument();
    expect(screen.queryByTestId('image-fallback')).toBeNull();
  });

  it('swaps to the placeholder when the image fails, calling onError once', () => {
    const onError = vi.fn();
    render(<SafeImage src="https://placehold.co/broken.png" alt="Silk" width={10} height={10} onError={onError} />);
    fireEvent.error(screen.getByAltText('Silk'));
    expect(onError).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('img', { name: 'Silk' })?.tagName).not.toBe('IMG');
    expect(screen.getByTestId('image-fallback')).toBeInTheDocument();
  });

  it('shows the placeholder when there is no URL', () => {
    render(<SafeImage src="" alt="Silk" width={10} height={10} />);
    expect(screen.getByTestId('image-fallback')).toBeInTheDocument();
  });

  it('retries when the src changes after a failure', () => {
    const { rerender } = render(<SafeImage src="https://placehold.co/a.png" alt="Silk" width={10} height={10} />);
    fireEvent.error(screen.getByAltText('Silk'));
    expect(screen.getByTestId('image-fallback')).toBeInTheDocument();
    rerender(<SafeImage src="https://placehold.co/b.png" alt="Silk" width={10} height={10} />);
    expect(screen.queryByTestId('image-fallback')).toBeNull();
    expect(screen.getByAltText('Silk')).toBeInTheDocument();
  });
});
