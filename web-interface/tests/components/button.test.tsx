import React from 'react';
import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { Button } from '../../components/ui/button';

test('renders primary variant button correctly', () => {
  render(<Button variant="primary">Render Clip</Button>);
  const btn = screen.getByRole('button', { name: /Render Clip/i });
  expect(btn).toBeInTheDocument();
  expect(btn).toHaveClass('btn-primary');
});
