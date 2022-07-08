
<template>
  <div>
    <b-row>
      <template>
        <b-col v-if="showFilter" sm="12" lg="12" md="12" class="my-3">
          <b-form-group
            label="Filter"
            label-for="filter-input"
            label-cols-sm="1"
            label-align-sm="right"
            label-size="sm"
            class="mb-0"
          >
            <b-input-group size="sm">
              <b-form-input
                id="filter-input"
                v-model="filter.text"
                type="search"
                placeholder="Type to Search"
              ></b-form-input>

              <b-input-group-append>
                <b-button :disabled="!filter.text" @click="filter.text = ''"
                  >Clear</b-button
                >
              </b-input-group-append>
            </b-input-group>
          </b-form-group>
        </b-col>
        <b-col sm="12" md="4" class="my-1">
          <b-form-group
            label="Per page"
            label-for="per-page-select"
            label-cols-sm="6"
            label-cols-md="5"
            label-cols-lg="4"
            label-align-sm="right"
            label-size="sm"
            class="mb-0"
          >
            <b-form-select
              id="per-page-select"
              v-model="perPage"
              :options="pageOptions"
              size="sm"
            ></b-form-select>
          </b-form-group>
        </b-col>

        <b-col sm="12" md="8" class="my-1">
          <b-pagination
            v-model="currentPage"
            :total-rows="totalRows"
            :per-page="perPage"
            align="fill"
            size="sm"
            class="my-0"
          ></b-pagination>
        </b-col>
      </template>
    </b-row>
    <b-table
      :items="items"
      :fields="fields"
      :current-page="currentPage"
      :per-page="perPage"
      :filter="filter.text"
      :filter-included-fields="filterOn"
      :sort-by.sync="sortBy"
      :sort-desc.sync="sortDesc"
      :sort-direction="sortDirection"
      stacked="md"
      show-empty
      small
      @filtered="onFiltered"
    >
      <template #cell(actions)="row">
        <b-button
          :disabled="loading"
          v-if="row.item.actions.edit ? row.item.actions.edit.show : false"
          variant="primary"
          size="sm"
          @click="editItem(row.item)"
          class="mr-1"
        >
          {{ row.item.actions.edit ? row.item.actions.edit.name : "Edit" }}
        </b-button>
        <b-button
          :disabled="loading"
          v-if="row.item.actions.delete ? row.item.actions.delete.show : false"
          variant="danger"
          @click="deleteItem(row.item)"
          size="sm"
        >
          {{
            row.item.actions.delete ? row.item.actions.delete.name : "Delete"
          }}
        </b-button>
        <b-button
          :disabled="loading"
          v-if="row.item.actions.show ? row.item.actions.show.show : false"
          variant="secondary"
          size="sm"
          @click="showItem(row.item)"
          class="mr-1"
        >
          {{ row.item.actions.show ? row.item.actions.show.name : "Show" }}
        </b-button>
      </template>
      <!-- <template #row-details="row">
        <b-card>
          <ul>
            <li v-for="(value, key) in row.item" :key="key">
              {{ key }}: {{ value }}
            </li>
          </ul>
        </b-card>
      </template> -->
    </b-table>
    <b-row> </b-row>
  </div>
</template>

<script>
export default {
  data() {
    return {
      totalRows: 1,
      currentPage: 1,
      perPage: 10,
      pageOptions: [10, 25, 50, { value: 100, text: "Show a lot" }],
      sortBy: "",
      sortDesc: false,
      sortDirection: "asc",
      filterOn: [],
    };
  },
  props: {
    items: {
      type: Array,
      required: true,
    },
    fields: {
      type: Array,
      required: true,
    },
    showFilter: {
      type: Boolean,
      default: true,
    },
    filter: {
      type: Object,
      default(rawProps) {
        return { text: "" };
      },
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  mounted() {
    this.totalRows = this.items.length;
  },
  methods: {
    editItem: function (item) {
      this.$emit("editObject", item);
    },
    showItem: function (item) {
      this.$emit("showObject", item);
    },
    deleteItem: function (item) {
      this.$emit("deleteObject", item);
    },
    onFiltered: function (filteredItems) {
      this.totalRows = filteredItems.length;
      this.currentPage = 1;
    },
  },
};
</script>