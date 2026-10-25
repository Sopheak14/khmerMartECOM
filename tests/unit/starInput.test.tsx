import React, { useState } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { StarRating } from '@/components/ui/star-rating';

describe('StarRating Component', () => {
  it('renders 5 star buttons with correct aria attributes', () => {
    render(<StarRating value={3} onChange={() => {}} label="Rate product" />);

    const radiogroup = screen.getByRole('radiogroup', { name: 'Rate product' });
    expect(radiogroup).toBeInTheDocument();

    const starButtons = screen.getAllByRole('radio');
    expect(starButtons).toHaveLength(5);

    expect(starButtons[0]).toHaveAttribute('aria-label', '1 of 5 stars');
    expect(starButtons[2]).toHaveAttribute('aria-checked', 'true');
    expect(starButtons[0]).toHaveAttribute('aria-checked', 'false');
  });

  it('triggers onChange when clicking a star', () => {
    const handleChange = vi.fn();
    render(<StarRating value={0} onChange={handleChange} />);

    const star4 = screen.getByTestId('star-4');
    fireEvent.click(star4);

    expect(handleChange).toHaveBeenCalledWith(4);
  });

  it('handles keyboard navigation: ArrowRight / ArrowLeft and number keys', () => {
    function ControlledRating() {
      const [rating, setRating] = useState(2);
      return <StarRating value={rating} onChange={setRating} />;
    }

    render(<ControlledRating />);

    const radiogroup = screen.getByRole('radiogroup');

    // Arrow Right -> 3
    fireEvent.keyDown(radiogroup, { key: 'ArrowRight' });
    expect(screen.getByTestId('star-3')).toHaveAttribute('aria-checked', 'true');

    // Arrow Right -> 4
    fireEvent.keyDown(radiogroup, { key: 'ArrowRight' });
    expect(screen.getByTestId('star-4')).toHaveAttribute('aria-checked', 'true');

    // Arrow Left -> 3
    fireEvent.keyDown(radiogroup, { key: 'ArrowLeft' });
    expect(screen.getByTestId('star-3')).toHaveAttribute('aria-checked', 'true');

    // Number key '5' -> 5
    fireEvent.keyDown(radiogroup, { key: '5' });
    expect(screen.getByTestId('star-5')).toHaveAttribute('aria-checked', 'true');

    // Number key '1' -> 1
    fireEvent.keyDown(radiogroup, { key: '1' });
    expect(screen.getByTestId('star-1')).toHaveAttribute('aria-checked', 'true');
  });

  it('renders error message when error prop is provided', () => {
    render(<StarRating value={0} error="Rating is required" />);
    expect(screen.getByTestId('error-rating')).toHaveTextContent('Rating is required');
  });
});
