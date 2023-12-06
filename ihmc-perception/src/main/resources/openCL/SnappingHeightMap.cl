#define HEIGHT_MAP_CENTER_X 0
#define HEIGHT_MAP_CENTER_Y 1
#define GLOBAL_CELL_SIZE 2
#define GLOBAL_CENTER_INDEX 3
#define CROPPED_WINDOW_CENTER_INDEX 4
#define HEIGHT_SCALING_FACTOR 5
#define HEIGHT_OFFSET 6
#define FOOT_LENGTH 7
#define FOOT_WIDTH 8
#define MIN_DISTANCE_FROM_CLIFF_TOPS 9
#define MIN_DISTANCE_FROM_CLIFF_BOTTOMS 10
#define CLIFF_START_HEIGHT_TO_AVOID 11
#define CLIFF_END_HEIGHT_TO_AVOID 12

#define SNAP_FAILED 0
#define CLIFF_TOP 1
#define CLIFF_BOTTOM 2
#define NOT_ENOUGH_AREA 0
#define VALID 4

#define ASSUME_FOOT_IS_A_CIRCLE true

float get_yaw_from_index(int yaw_discretizations, int idx_yaw)
{
    return M_PI_F * ((float) idx_yaw) / ((yaw_discretizations - 1));
}

float signed_distance_to_foot_polygon(int center_index, float resolution, global float* params, int2 foot_key, float foot_yaw, int2 query)
{
    int cells_per_side = 2 * center_index + 1;
    int map_idx_x = cells_per_side - query.y;
    int map_idx_y = query.x;
    // we're flipping the axes because it's image vs world
    float2 vector_to_point = resolution * (float2) ((float) (query.x - foot_key.x), (float) (query.y - foot_key.y));

    float2 vector_in_foot_frame = applyYawRotationToVector2D(vector_to_point, -foot_yaw);

    float x_outside = fabs(vector_in_foot_frame.x) - params[FOOT_LENGTH] / 2.0f;
    float y_outside = fabs(vector_in_foot_frame.y) - params[FOOT_WIDTH] / 2.0f;

    if (x_outside > 0.0f && y_outside > 0.0f)
    {
        return length((float2) (x_outside, y_outside));
    }
    else if (x_outside > 0.0f)
    {
        return x_outside;
    }
    else if (y_outside > 0.0f)
    {
        return y_outside;
    }
    else
    {
        return max(x_outside, y_outside);
    }
}

float signed_distance_to_foot_circle(int center_index, float resolution, global float* params, int2 foot_key, int2 query)
{
    int cells_per_side = 2 * center_index + 1;
    int map_idx_x = cells_per_side - query.y;
    int map_idx_y = query.x;
    // we're flipping the axes because it's image vs world
    float2 vector_to_point = resolution * (float2) ((float) (query.x - foot_key.x), (float) (query.y - foot_key.y));
    float foot_distance = length((float2) (0.5 * params[FOOT_LENGTH], 0.5 * params[FOOT_WIDTH]));

    return length(vector_to_point) - foot_distance;
}

float get_height_on_plane(float x, float y, global float *plane)
{
   float height = (plane[3] - (plane[0] * x + plane[1] * y)) / plane[2];
   return height;
}

void kernel computeSnappedValuesKernel(global float* params,
                                read_write image2d_t height_map,
                                global float* idx_yaw_singular_buffer,
                                read_write image2d_t steppable_map,
                                read_write image2d_t snapped_height_map,
                                read_write image2d_t snapped_normal_x_map,
                                read_write image2d_t snapped_normal_y_map,
                                read_write image2d_t snapped_normal_z_map)
{
    // Remember, these are x and y in image coordinates, not world
    int idx_x = get_global_id(0); // column, top left
    int idx_y = get_global_id(1); // row, top left
    int idx_yaw = (int) idx_yaw_singular_buffer[0]; // TODO not needed

    bool should_print = false;//idx_x == 20 && idx_y == 20;

    int2 key = (int2) (idx_x, idx_y);
    uint foot_height_int = read_imageui(height_map, key).x;

    float foot_height = (float) foot_height_int / params[HEIGHT_SCALING_FACTOR] - params[HEIGHT_OFFSET];

    // TODO yaw discretizations should get set
    float foot_yaw = get_yaw_from_index(2, idx_yaw);

    float foot_width = params[FOOT_WIDTH];
    float foot_length = params[FOOT_LENGTH];
    float distance_from_bottom = params[MIN_DISTANCE_FROM_CLIFF_BOTTOMS];
    float distance_from_top = params[MIN_DISTANCE_FROM_CLIFF_TOPS];

    float map_resolution = params[GLOBAL_CELL_SIZE];
    float max_dimension = max(params[FOOT_WIDTH], params[FOOT_LENGTH]);
    int map_center_index = params[GLOBAL_CENTER_INDEX];
    int cropped_center_index = params[CROPPED_WINDOW_CENTER_INDEX];
    float2 center = (float2) (params[HEIGHT_MAP_CENTER_Y], params[HEIGHT_MAP_CENTER_X]);
    float2 map_center = (float2) (0.0, 0.0);

    int map_cells_per_side = 2 * map_center_index + 1;
    int cropped_cells_per_side = 2 * cropped_center_index + 1;

    int crop_idx_x = idx_x;
    int crop_idx_y = idx_y;
    int2 crop_key = (int2) (crop_idx_x, crop_idx_y);

    // FIXME why is this negative
    float2 foot_position = indices_to_coordinate(crop_key, center, map_resolution, cropped_center_index);

    // Convert from the world coordinate to the map index.
    int2 map_key = coordinate_to_indices(foot_position, map_center, map_resolution, map_center_index);

    ////// Find the maximum height of any point underneath the foot

    float half_length = foot_length / 2.0f;
    float half_width = foot_width / 2.0f;
    float foot_search_radius = length((float2) (half_length, half_width));
    int foot_offset_indices = (int) ceil(foot_search_radius / map_resolution);

    float max_height_under_foot = -INFINITY;

    for (int x_query = map_key.x - foot_offset_indices; x_query <= map_key.x + foot_offset_indices; x_query++)
    {
        // if we're outside of the search area in the x direction, skip to the end
        // FIXME should this include an equals?
        if (x_query < 0 || x_query >= map_cells_per_side)
            continue;

        for (int y_query = map_key.y - foot_offset_indices; y_query <= map_key.y + foot_offset_indices; y_query++)
        {
            // y is out of bounds, so skip it
            // FIXME should this include an equals?
            if (y_query < 0 || y_query >= map_cells_per_side)
                continue;

            float2 vector_to_point_from_foot = map_resolution * (float2) ((float) (x_query - map_key.x), (float) (y_query - map_key.y));
            // TODO use squared value to avoid the squareroot operator
            if (length(vector_to_point_from_foot) > foot_search_radius)
                continue;

            // get the height at this offset point.
            int2 query_key = (int2) (x_query, y_query);
            float query_height = (float) read_imageui(height_map, query_key).x / params[HEIGHT_SCALING_FACTOR] - params[HEIGHT_OFFSET];

            float distance_to_foot_from_this_query;
            if (ASSUME_FOOT_IS_A_CIRCLE)
            {
                distance_to_foot_from_this_query = signed_distance_to_foot_circle(map_center_index, map_resolution, params, map_key, query_key);
            }
            else
            {
                distance_to_foot_from_this_query = signed_distance_to_foot_polygon(map_center_index, map_resolution, params, map_key, foot_yaw, query_key);
            }

            //  TODO extract epsilon
            // We want to find the max heigth under the foot. However, we don't want points that are very close to the foot edge to unduly affect
            // this height, because that can some times be noise. So instead, we can use an activation function. Increasing the slope of the activation
            // function will increase how quickly these get added, where a slope of infinity makes it an inequality condition.
//            float tanh_slope = 50000.0f;
//            float activation = 1.0f - 0.5f * (tanh(tanh_slope * distance_to_foot_from_this_query) + 1.0f);
//            max_height_under_foot += activation * max(max_height_under_foot - query_height, 0.0f);

            // FIXME old code, where we're trying to smooth things
            if (distance_to_foot_from_this_query < 1e-3f)
            {
                max_height_under_foot = max(query_height, max_height_under_foot);
            }
        }
    }

    //////// Compute the local plane of the foot, as well as the area of support underneath it.

    float min_height_under_foot_to_consider = max_height_under_foot - 0.05f; // TODO extract parameter

    // these are values that will be used for the plane calculation
    float n = 0.0f;
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
    float xx = 0.0f;
    float xy = 0.0f;
    float xz = 0.0f;
    float yy = 0.0f;
    float yz = 0.0f;
    float zz = 0.0f;

    int max_points_possible_under_support;

    if (ASSUME_FOOT_IS_A_CIRCLE)
    {
        int samples = 5;
         // fixme get this resolution right.
        float resolution = (2.0f * foot_search_radius) / (2.0f * samples + 1.0f);

        max_points_possible_under_support = 0;

        for (int x_value_idx = -samples; x_value_idx <= samples; x_value_idx++)
        {
            for (int y_value_idx = -samples; y_value_idx <= samples; y_value_idx++)
            {
                float2 offset = resolution * (float2) (x_value_idx, y_value_idx);
                float offset_distance  = length(offset);

                // TODO replace with a squared operation
                if (offset_distance > foot_search_radius)
                    continue;

                // TODO do we want to put this after the check below? That would allow it to consider different sizes based on the FOV
                max_points_possible_under_support++;

                float2 point_query = offset + foot_position;

                int2 query_key = coordinate_to_indices(point_query, map_center, map_resolution, map_center_index);

                // This query position is out of bounds of the incoming height map, so it should be skipped.
                if (query_key.x < 0 || query_key.x > map_cells_per_side - 1 || query_key.y < 0 || query_key.y > map_cells_per_side - 1)
                    continue;

                uint query_height_int = read_imageui(height_map, query_key).x;
                float query_height = (float) query_height_int / params[HEIGHT_SCALING_FACTOR] - params[HEIGHT_OFFSET];

                // FIXME old code
//                if (isnan(query_height) || query_height < min_height_under_foot_to_consider)
//                    continue;
//
                //float activation = 1.0;

                if (isnan(query_height))
                   continue;

                min_height_under_foot_to_consider = max_height_under_foot - (0.03f + 0.06f * clamp(offset_distance / foot_search_radius, 0.0f, 1.0f));

//            // fixme extract
                float tanh_slope = 50000.0f;
                float activation = 0.5f * (tanh(tanh_slope * (query_height - min_height_under_foot_to_consider)) + 1.0f);

                float activation2 = activation * activation;

                n += activation;
                x += activation * point_query.x;
                y += activation * point_query.y;
                z += activation * query_height;
                xx += activation2 * point_query.x * point_query.x;
                xy += activation2 * point_query.x * point_query.y;
                xz += activation2 * point_query.x * query_height;
                yy += activation2 * point_query.y * point_query.y;
                yz += activation2 * point_query.y * query_height;
                zz += activation2 * query_height * query_height;
            }
        }
    }
    else
    {
        float resolution = 0.02f;

        for (float x_value = -half_length; x_value <= half_length; x_value += resolution)
        {
            for (float y_value = -half_width; y_value <= half_width; y_value += resolution)
            {
                float2 vector_in_foot = applyYawRotationToVector2D((float2) (x_value, y_value), foot_yaw);
                float2 point_query = vector_in_foot + foot_position;

                int map_query_x = coordinate_to_index(point_query.x, map_center.x, map_resolution, map_center_index);
                int map_query_y = coordinate_to_index(point_query.y, map_center.y, map_resolution, map_center_index);

                int image_query_x = map_query_y;
                int image_query_y = map_cells_per_side - map_query_x;

                // This query position is out of bounds of the incoming height map, so it should be skipped.
                if (image_query_x < 0 || image_query_x > map_cells_per_side - 1 || image_query_y < 0 || image_query_y > map_cells_per_side - 1)
                    continue;

                int2 query_key = (int2) (image_query_x, image_query_y);
                uint query_height_int = read_imageui(height_map, query_key).x;

                float query_height = (float) query_height_int / params[HEIGHT_SCALING_FACTOR] - params[HEIGHT_OFFSET];

                if (isnan(query_height) || query_height < min_height_under_foot_to_consider)
                   continue;

                n += 1.0f;
                x += point_query.x;
                y += point_query.y;
                z += query_height;
                xx += point_query.x * point_query.x;
                xy += point_query.x * point_query.y;
                xz += point_query.x * query_height;
                yy += point_query.y * point_query.y;
                yz += point_query.y * query_height;
                zz += query_height * query_height;
            }
        }

        int lengths = (int) floor(foot_length / resolution);
        int widths = (int) floor(foot_width / resolution);
        max_points_possible_under_support = lengths * widths;
    }

    ///////////// Solve for the plane normal, as well as the height of the foot along that plane.
    bool failed = false;
    int snap_result = VALID;

    // FIXME for some reason all the points were bad
    if (n < 0.0001f)
    {
//        snap_result = SNAP_FAILED;
        failed = true;
        n = 1.0f;
    }
    // This is the actual height of the snapped foot
    float snap_height = z / n;


    float covariance_matrix[9] = {xx, xy, x, xy, yy, y, x, y, n};
    float z_variance_vector[3] = {-xz, -yz, -z};
    float coefficients[3] = {0.0f, 0.0f, 0.0f};
    solveForPlaneCoefficients(covariance_matrix, z_variance_vector, coefficients);

    float x_solution = x / n;  // average of x positions
    float y_solution = y / n;  // average of y positions
    float z_solution = -coefficients[0] * x_solution - coefficients[1] * y_solution - coefficients[2];

    float3 normal = (float3) (coefficients[0], coefficients[1], 1.0);
    normal = normalize(normal);

    // FIXME remove this?
   // snap_height = getZOnPlane(foot_position, (float3) (x_solution, y_solution, z_solution), normal);


    //////////// Check to make sure we're not stepping too near a cliff base or top

    float cliff_search_offset = max_dimension / 2.0f + max(params[MIN_DISTANCE_FROM_CLIFF_BOTTOMS], params[MIN_DISTANCE_FROM_CLIFF_TOPS]);
    int cliff_offset_indices = (int) ceil(cliff_search_offset / map_resolution);

    // search for a cliff base that's too close
    for (int x_query = map_key.x - cliff_offset_indices && !failed; x_query <= map_key.x + cliff_offset_indices && !failed; x_query++)
    {
        // if we're outside of the search area in the x direction, skip to the end
        if (x_query < 0 || x_query >= map_cells_per_side)
            continue;

        for (int y_query = map_key.y - cliff_offset_indices && !failed; y_query <= map_key.y + cliff_offset_indices; y_query++)
        {
            // y is out of bounds, so skip it
            if (y_query < 0 || y_query >= map_cells_per_side)
                continue;

            float2 vector_to_point_from_foot = map_resolution * (float2) ((float) (x_query - map_key.x), (float) (y_query - map_key.y));
            // TODO use squared value to avoid the squareroot operator
            if (length(vector_to_point_from_foot) > cliff_search_offset)
                continue;

            // get the height at this offset point.
            int2 query_key = (int2) (x_query, y_query);
            float query_height = (float) read_imageui(height_map, query_key).x / params[HEIGHT_SCALING_FACTOR] - params[HEIGHT_OFFSET];

            // compute the relative height at this point, compared to the height contained in the current cell.
            float relative_height_of_query = query_height - snap_height;

            float distance_to_foot_from_this_query;
            if (ASSUME_FOOT_IS_A_CIRCLE)
            {
                distance_to_foot_from_this_query = signed_distance_to_foot_circle(map_center_index, map_resolution, params, map_key, query_key);
            }
            else
            {
                distance_to_foot_from_this_query = signed_distance_to_foot_polygon(map_center_index, map_resolution, params, map_key, foot_yaw, query_key);
            }

           // FIXME so this being a hard transition makes it a noisy signal. How can we smooth it?
            if (relative_height_of_query > params[CLIFF_START_HEIGHT_TO_AVOID])
            {
                float height_alpha = (relative_height_of_query - params[CLIFF_START_HEIGHT_TO_AVOID]) / (params[CLIFF_END_HEIGHT_TO_AVOID] - params[CLIFF_START_HEIGHT_TO_AVOID]);
                height_alpha = clamp(height_alpha, 0.0f, 1.0f);
                float min_distance_from_this_point_to_avoid_cliff = height_alpha * params[MIN_DISTANCE_FROM_CLIFF_BOTTOMS];


                if (distance_to_foot_from_this_query < min_distance_from_this_point_to_avoid_cliff)
                {
                                    printf("near cliff bottom\n");

                    // we're too close to the cliff bottom!
                    snap_result = CLIFF_BOTTOM;
                    failed = true;
                    break;
                }
            }
            else if (relative_height_of_query < -params[CLIFF_START_HEIGHT_TO_AVOID])
            {
                printf("Checking if cliff top\n");

                // FIXME so this being a hard transition makes it a noisy signal. How can we smooth it?
                if (distance_to_foot_from_this_query < params[MIN_DISTANCE_FROM_CLIFF_TOPS])
                {
                    printf("near cliff top\n");
                    // we're too close to the cliff top!
                    snap_result = CLIFF_TOP;
                    failed = true;
                    break;
                }
            }
        }
    }

    /////////////// Make sure there's enough step area.

    // TODO extract area fraction
    float min_area_fraction = 0.9f;
    float min_points_needed_for_support = (int) (min_area_fraction * max_points_possible_under_support);
    if (n < max_points_possible_under_support)
    {
        //printf("only %f supported of the total %d. %f needed\n", n, max_points_possible_under_support, min_points_needed_for_support);
    }
    // FIXME this hard inequality is causing some noise in the solution space. How could we reduce that?
    if (n < min_points_needed_for_support)
    {
   //     snap_result = NOT_ENOUGH_AREA;
    }

    snap_height += params[HEIGHT_OFFSET];

    int2 storage_key = (int2) (idx_x, idx_y);
    write_imageui(steppable_map, storage_key, (uint4)(snap_result,0,0,0));
    write_imageui(snapped_height_map, storage_key, (uint4)((int)(snap_height * params[HEIGHT_SCALING_FACTOR]), 0, 0, 0));
    write_imageui(snapped_normal_x_map, storage_key, (uint4)((int)(normal.x * params[HEIGHT_SCALING_FACTOR]), 0, 0, 0));
    write_imageui(snapped_normal_y_map, storage_key, (uint4)((int)(normal.y * params[HEIGHT_SCALING_FACTOR]), 0, 0, 0));
    write_imageui(snapped_normal_z_map, storage_key, (uint4)((int)(normal.z * params[HEIGHT_SCALING_FACTOR]), 0, 0, 0));
}
