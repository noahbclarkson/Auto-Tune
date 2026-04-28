export interface AuctionDepthData {
  material: string;
  depth: number;
  bids: Array<{
    id: string;
    price: number;
    remainingQuantity: number;
    totalValue: number;
  }>;
  asks: Array<{
    id: string;
    price: number;
    remainingQuantity: number;
    totalValue: number;
  }>;
}
