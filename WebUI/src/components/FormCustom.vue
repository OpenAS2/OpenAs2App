<template>
  <b-form @submit.stop.prevent="submitForm">
    <template v-for="(field, index) in schema.fields">
      <template v-if="field.type === 'select'">
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label="field.label"
          :label-for="field.name"
        >
          <!-- :value-field="field.value ? field.value : 'hh'" -->
          <b-form-select
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            :name="field.name"
            v-model="model[field.name]"
            :type="field.type"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :options="field.list"
            :aria-describedby="`${field.name}-feedback`"
          ></b-form-select>
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
      <template v-else-if="field.type === 'textarea'">
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label="field.label"
          :label-for="field.name"
        >
          <!-- :value-field="field.value ? field.value : 'hh'" -->
          <b-form-textarea
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            :name="field.name"
            v-model="model[field.name]"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :options="field.list"
            :aria-describedby="`${field.name}-feedback`"
            rows="5"
            max-rows="6"
          ></b-form-textarea>
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
      <template v-else-if="field.type === 'datepicker'">
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label="field.label"
          :label-for="field.name"
        >
          <b-form-datepicker
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            :name="field.name"
            v-model="model[field.name]"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :options="field.list"
            :aria-describedby="`${field.name}-feedback`"
          ></b-form-datepicker>
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
      <template v-else-if="field.type === 'file'">
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label="field.label"
          :label-for="field.name"
        >
          <b-form-file
            :accept="field.accept?field.accept:'*'"
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            :name="field.name"
            v-model="model[field.name]"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :options="field.list"
            :aria-describedby="`${field.name}-feedback`"
            @change="fileUpload"
          ></b-form-file>
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
      <template v-else-if="field.type === 'checkbox'">
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label-for="field.name"
        >
          <!-- :label="field.label" -->
          <b-form-checkbox
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            v-model="model[field.name]"
            :name="field.name"
            :type="field.type"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :aria-describedby="`${field.name}-feedback`"
            >{{ field.label }}</b-form-checkbox
          >
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
      <template v-else>
        <b-form-group
          v-if="field.show!=undefined?field.show:true"
          :key="index"
          :id="`type-${field.name}`"
          :label="field.label"
          :label-for="field.name"
        >
          <b-form-input
            :disabled="field.disabled?field.disabled:false"
            :id="field.name"
            v-model="model[field.name]"
            :name="field.name"
            :type="field.type"
            :placeholder="field.placeholder"
            v-validate="field._validate"
            :state="validateState(field.name)"
            :data-vv-as="field.label"
            :aria-describedby="`${field.name}-feedback`"
          ></b-form-input>
          <b-form-invalid-feedback :id="`${field.name}-feedback`">{{
            veeErrors.first(field.name)
          }}</b-form-invalid-feedback>
        </b-form-group>
      </template>
    </template>
    <b-row>
      <!-- <b-col col > -->
      <b-button-group>
        <b-button :disabled="loading" type="submit" variant="success" size="sm">Save</b-button>
        <b-button :disabled="loading" @click="cancelForm" size="sm">Cancel</b-button>
      </b-button-group>
      <!-- </b-col> -->
    </b-row>
  </b-form>
</template>
<script>
export default {
  props: {
    schema: {
      type: Object,
      require: true,
    },
    model: {
      type: Object,
      required: true,
    },
    title: {
      type: String,
      required: false,
    },
    loading:{
      type:Boolean,
      default:false
    }
  },
  data() {
    return {
      data: {},
    };
  },
  mounted: async function () {
    this.data = this.model;
    // this.model.then((e) => {
    //   this.data = e;
    // });
  },
  methods: {
    validateState: function (ref) {
      if (
        this.veeFields[ref] &&
        (this.veeFields[ref].dirty || this.veeFields[ref].validated)
      ) {
        return !this.veeErrors.has(ref);
      }
      return null;
    },
    submitForm: function (e) {
      e.preventDefault();
      this.$validator.validateAll().then((result) => {
        if (!result) {
          return;
        }
        this.$emit("commit", this.data);
      });
    },
    cancelForm: function (e) {
      this.$emit("revert", this.data._id);
    },
    fileUpload: function (e) {
      var oFile = document.getElementById(e.target.name).files[0];
      console.log("e",e.target.name)
      console.log("onfile",oFile);
      if(oFile.size > 0){
        console.log("onfile");
        this.$emit("fileUpload",{file:oFile,e})
      }
      // console.log("eeeee",e);

    },
  },
};
</script>