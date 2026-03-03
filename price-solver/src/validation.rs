//! Input validation for ratio matrices

use thiserror::Error;

/// Validation errors
#[derive(Error, Debug)]
pub enum ValidationError {
    #[error("Matrix dimension mismatch: expected {expected}x{expected}, got row {row} with {cols} columns")]
    DimensionMismatch {
        expected: usize,
        row: usize,
        cols: usize,
    },

    #[error("Invalid ratio at [{i}][{j}]: {value}. All ratios must be positive.")]
    InvalidRatio { i: usize, j: usize, value: f64 },

    #[error("Diagonal must be 1.0, found {value} at [{i}][{j}]")]
    InvalidDiagonal { i: usize, j: usize, value: f64 },

    #[error("Matrix is not reciprocal: r[{i}][{j}]={rij} but r[{j}][{i}]={rji}")]
    NotReciprocal {
        i: usize,
        j: usize,
        rij: f64,
        rji: f64,
    },

    #[error("Empty matrix")]
    EmptyMatrix,
}

/// Validate a single ratio matrix
///
/// Checks:
/// - Square matrix
/// - Diagonal is all 1.0
/// - All values are positive
/// - Reciprocity: r[i][j] ≈ 1/r[j][i]
pub fn validate_ratio_matrix(matrix: &[Vec<f64>]) -> Result<(), ValidationError> {
    let n = matrix.len();
    if n == 0 {
        return Err(ValidationError::EmptyMatrix);
    }

    for i in 0..n {
        let row = &matrix[i];
        if row.len() != n {
            return Err(ValidationError::DimensionMismatch {
                expected: n,
                row: i,
                cols: row.len(),
            });
        }

        for j in 0..n {
            let val = row[j];

            // Check positivity
            if val <= 0.0 {
                return Err(ValidationError::InvalidRatio { i, j, value: val });
            }

            // Check diagonal
            if i == j && (val - 1.0).abs() > 0.001 {
                return Err(ValidationError::InvalidDiagonal { i, j, value: val });
            }

            // Check reciprocity (tolerance for floating point)
            if i < j {
                let reciprocal = matrix[j][i];
                let expected_reciprocal = 1.0 / val;
                if (reciprocal - expected_reciprocal).abs() > 0.001 {
                    return Err(ValidationError::NotReciprocal {
                        i,
                        j,
                        rij: val,
                        rji: reciprocal,
                    });
                }
            }
        }
    }

    Ok(())
}

/// Validate multiple ratio matrices have consistent dimensions
pub fn validate_consistent_dimensions(
    matrices: &[Vec<Vec<f64>>],
) -> Result<usize, ValidationError> {
    if matrices.is_empty() {
        return Err(ValidationError::EmptyMatrix);
    }

    let n = matrices[0].len();
    for matrix in matrices {
        validate_ratio_matrix(matrix)?;
        if matrix.len() != n {
            return Err(ValidationError::DimensionMismatch {
                expected: n,
                row: 0,
                cols: matrix.len(),
            });
        }
    }

    Ok(n)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_matrix() {
        let matrix = vec![
            vec![1.0, 2.0, 3.0],
            vec![0.5, 1.0, 1.5],
            vec![0.333, 0.667, 1.0],
        ];
        assert!(validate_ratio_matrix(&matrix).is_ok());
    }

    #[test]
    fn test_negative_value() {
        let matrix = vec![vec![1.0, -2.0], vec![-0.5, 1.0]];
        assert!(matches!(
            validate_ratio_matrix(&matrix),
            Err(ValidationError::InvalidRatio { .. })
        ));
    }

    #[test]
    fn test_non_reciprocal() {
        let matrix = vec![
            vec![1.0, 2.0],
            vec![0.6, 1.0], // Should be 0.5
        ];
        assert!(matches!(
            validate_ratio_matrix(&matrix),
            Err(ValidationError::NotReciprocal { .. })
        ));
    }

    #[test]
    fn test_wrong_diagonal() {
        let matrix = vec![vec![2.0, 1.0], vec![1.0, 1.0]];
        assert!(matches!(
            validate_ratio_matrix(&matrix),
            Err(ValidationError::InvalidDiagonal { .. })
        ));
    }
}
