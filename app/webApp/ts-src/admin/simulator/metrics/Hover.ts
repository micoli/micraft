export interface Hover {
  /**
   * Position in the column list, not the column itself: the series is rebuilt every time the server
   * pushes, so holding the object would leave the card showing a bucket that no longer exists.
   */
  index: number;
  /** Pointer position inside the chart box, in pixels. */
  x: number;
  y: number;
}
